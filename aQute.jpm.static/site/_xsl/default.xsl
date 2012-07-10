<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	version="1.0">
	<xsl:output method="html" doctype-system="about:legacy-compat"
		encoding="UTF-8" indent="no" />


	<xsl:variable name="site" select="document('site')/site" />

	<xsl:template match="/">
		<xsl:param name="path" />
		<html lang="en" ng-app="jpm">
			<head>
				<meta charset="utf-8" />
				<meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1" />
				<meta name="viewport" content="width=device-width, initial-scale=1" />
				<link href="css/style.css" rel="stylesheet" />
				<title>
					<xsl:value-of select="$site/@prefix" />
					-
					<xsl:value-of select="//h1[1]" />
				</title>
			</head>

			<body ng-controller="JPM">
								    <img class="xuser" ng-src="/rest/gravatar/{{user.email}}?s=16" /> {{user.email}}
			
				<div id="nav">
					<ul>
						<li>
							<!-- Reserved for logo -->
							<a id="logo" href="index.html">&#160;</a>
						</li>
						<li>
							<a href="#/doc/install">Install</a>
						</li>
						<li>
							<a href="#/doc/about">About</a>
						</li>
						<li>
							<input type='text' ng-model="query" required="true" />
							<button ng-click="search(query)" ng-disabled="!canSearch()">Go</button>
						</li>
						<li>
							<div class="ng-cloak">
								<span ng-show="user">
								    <img class="xuser" ng-src="/rest/gravatar/{{user.email}}?s=16" /> 
									{{user.email}}
									<a ng-click="logout()">Logout</a>
								</span>
								<span ng-hide="user">
									<a ng-click="login()">Login</a>
								</span>
							</div>
						</li>
					</ul>
				</div>
				<div id="navreserve" />
				<div id="page">
					<div id="external">
						<xsl:copy-of select="content/*" />
					</div>
					<div id="side">
						<h2>Sidebars</h2>

					</div>
				</div>

				<div id="footer">
					<div id="aQute">
						<h2>aQute</h2>
						<ul>
							<li>
								<a href="about.html">About</a>
							</li>
							<li>
								<a href="about.html">About</a>
							</li>
							<li>
								<a href="about.html">About</a>
							</li>
							<li>
								<a href="about.html">About</a>
							</li>
						</ul>
					</div>
					<div id="tools">
						<h2>Tools</h2>
						<ul>
							<li>
								<a href="about.html">About</a>
							</li>
							<li>
								<a href="about.html">About</a>
							</li>
							<li>
								<a href="about.html">About</a>
							</li>
							<li>
								<a href="about.html">About</a>
							</li>
						</ul>
					</div>
					<div id="extras">
						<h2>Extras</h2>
						<ul>
							<li>
								<a href="about.html">About</a>
							</li>
							<li>
								<a href="about.html">About</a>
							</li>
							<li>
								<a href="about.html">About</a>
							</li>
							<li>
								<a href="about.html">About</a>
							</li>
						</ul>
					</div>
					<div id="documentation">
						<h2>Documentation</h2>
						<ul>
							<li>
								<a href="about.html">About</a>
							</li>
							<li>
								<a href="about.html">About</a>
							</li>
							<li>
								<a href="about.html">About</a>
							</li>
							<li>
								<a href="about.html">About</a>
							</li>
						</ul>
					</div>
				</div>

				<script src="https://browserid.org/include.js" type="text/javascript" />
				<script src="js/angular-1.0.0.js" type="text/javascript" />
				<script src="js/angular-resource-1.0.0.js" type="text/javascript" />
				<script src="js/jpm.js" type="text/javascript" />
			</body>
		</html>
	</xsl:template>

</xsl:stylesheet>