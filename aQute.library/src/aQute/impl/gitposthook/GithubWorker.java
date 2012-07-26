package aQute.impl.gitposthook;

import java.net.*;
import java.util.*;

import javax.servlet.*;

import org.osgi.service.http.*;

import aQute.bnd.annotation.component.*;
import aQute.bnd.annotation.component.Reference;
import aQute.impl.gitposthook.Data.ImportData;
import aQute.libg.reporter.*;
import aQute.service.email.*;
import aQute.service.github.*;
import aQute.service.library.*;
import aQute.service.library.Library.Revision;
import aQute.service.logger.*;
import aQute.service.task.*;

/**
 * From IP addressses 207.97.227.253, 50.57.128.197, 108.171.174.178.
 */
@Component(provide = {
		Worker.class, GithubWorker.class
})
public class GithubWorker implements Worker<Data.ImportData> {

	public static abstract class GithubImporter extends ReporterAdapter implements Runnable {

	}

	public interface GithubWorkerMessages extends Messages {

		ERROR UnableToImport_From_(String sha, String string);

	}

	interface Messages extends LogMessages {

	}

	Github				github;
	Library				library;
	Email				smtp;
	private Messages	msgs;

	public void execute(final ImportData work) throws Exception {
		final Repository repo = github.getRepository(work.posthook.repository.name)
				.owner(work.posthook.repository.owner.name).get();

		GithubImporter ra = new GithubImporter() {

			public void run() {
				try {
					setTrace(true);
					Set<URI> uris = new HashSet<URI>();
					scan(uris);
					// delta(uris);

					StringBuilder sb = new StringBuilder();
					Formatter format = new Formatter(sb);
					format.format("Imported from Github, %s, from %s, for repository %s\n\n", new Date(work.time),
							work.ip, work.posthook.repository.name);

					format.format("%-40s %1s %-15s %-20s %6s %s\n", "Symbolic Name", "M", "Version", "Qualifier",
							"Errors", "Summary");

					int n = 0;

					for (URI url : uris) {
						if (isTrace())
							System.out.println(url);

						Library.Importer imp = library.importer(url)//
								.owner(work.posthook.repository.owner.email) //
								.message("From github repo " + work.posthook.repository.name)//
								.receipt(url.toString())//
								.trace(true);
						Revision revision = imp.fetch();
						if (revision != null) {
							format.format("%-40s %1s %-15s %-20s %6d %s%s\n", //
									revision.bsn, //
									revision.master ? 'm' : ' ', //
									revision.version.base, //
									revision.version.qualifier == null ? "" : revision.version.qualifier, //
									imp.getErrors().size(), //
									revision.summary == null ? "" : revision.summary, //
									(imp.isDuplicate() ? "(Was duplicate)" : "")//
							);
						} else {
							format.format("%-40s %1s %-15s %-20s %6d %s\n", //
									"", // bsn
									"", // master
									"", // version base
									"", // version qualifier
									imp.getErrors().size(), // errors
									"Failed to import " + imp.getURL() // summary
							);
						}
						getInfo(imp, n++ + ": ");
					}

					report(sb);

					if (isOk()) {
						sb.append("\nNo errors detected.\n");
						smtp.subject("Imported from Github repository %s at", work.posthook.repository.name, new Date())
								. //
								to(work.posthook.repository.owner.email). //
								text(sb.toString()).//
								send();
					} else {
						smtp.subject("Failure when imported from Github repository %s at %s",
								work.posthook.repository.name, new Date()) //
								.to(work.posthook.repository.owner.email)//
								.text(sb.toString())//
								.send();
					}
				}
				catch (Exception e) {
					e.printStackTrace();
					msgs.failed("Importing", e);
					error("During import %s", e);
				}

			}

			void scan(Set<URI> uris) throws Exception {
				List<Branch> branches = repo.getBranches();
				for (Branch branch : branches) {
					Commit commit = repo.getCommit(branch.commit.sha);
					scan(commit, uris);
				}
			}

			private void scan(Commit commit, Set<URI> uris) throws Exception {
				Tree tree = repo.getTree(commit.tree.sha);
				collect(commit, tree, "", uris);
			}

			private void collect(Commit commit, Tree tree, String name, Set<URI> uris) throws Exception {
				for (final Entry entry : tree.tree) {
					if (entry.path.endsWith(".jar")) {
						importEntry(commit, entry, name + "/" + entry.path, uris);
					}
					if (entry.type == Entry.Type.tree) {
						collect(commit, repo.getTree(entry.sha), name + "/" + entry.path, uris);
					}
				}
			}

			private void importEntry(final Commit commit, final Entry entry, final String path, Set<URI> uris)
					throws Exception {
				URI uri = repo.getURI(commit, path);
				uris.add(uri);
			}

			// void delta(Set<String> uris) throws Exception {
			//
			// String owner = work.posthook.repository.owner.name;
			// String repo = work.posthook.repository.name;
			//
			// Repository r = github.getRepository(repo).owner(owner).get();
			//
			// for (Delta c : work.posthook.commits) {
			// Commit commit = r.getCommit(c.id);
			// Tree tree = r.getTree(commit.tree.sha);
			//
			// trace("found %s", c.url);
			// for (String path : c.added) {
			// Entry entry = r.getEntry(tree, path);
			// importEntry(commit, entry, path, uris);
			// }
			// for (String path : c.modified) {
			// Entry entry = r.getEntry(tree, path);
			// importEntry(commit, entry, path, uris);
			// }
			// }
			//
			// }

		};
		ra.run();

		if (!ra.getErrors().isEmpty()) {
			msgs.failed(ra.toString());
		}
	}

	@Reference
	void setGithub(Github github) {
		this.github = github;
	}

	@Reference
	void setLibrary(Library lib) throws Exception {
		this.library = lib;
	}

	@Reference
	void setLog(Log log) throws ServletException, NamespaceException {
		msgs = log.logger(Messages.class);
	}

	@Reference
	void setEmail(Email email) throws ServletException, NamespaceException {
		this.smtp = email;
	}

}
