package test;

import junit.framework.*;
import aQute.aws.*;
import aQute.aws.credentials.*;
import aQute.aws.ses.*;

public class SESTest extends TestCase {
	UserCredentials	uc	= new UserCredentials();

	public void test() throws Exception {
		AWS aws = new AWS(uc.getAWSAccessKeyId(), uc.getAWSSecretKey());
		SES ses = aws.ses();
		String from = ses.subject("Hello Peter").to("ses@aqute.biz").text("Hello peter").from("ses@aqute.biz").send();
		System.out.println(from);
		assertNotNull(from);
	}

}
