routes 	= ($routeProvider) -> 
     $routeProvider.
       when('/admin',  			{ templateUrl: '/jpm/admin.htm', 	controller: AdminCtl }).
       when('/p/:bsn/r/:rev',	{ templateUrl: '/jpm/program.htm', 	controller: ProgramCtl }).
       when('/p/:bsn',			{ templateUrl: '/jpm/program.htm', 	controller: ProgramCtl }).
       when('/',	  			{ templateUrl: '/jpm/search.htm', 	controller: SearchCtl }).
       otherwise( 		    	{ redirectTo: '/' } )


activate = ( $resource, $location, $routeParams ) ->
    Program = $resource('/rest/program/:bsn',{}, {
      'get': {method:'GET', params: {}},
      'query': {method: 'GET', params:{query:@query,start:@start,limit:PAGE_SIZE}, isArray:true}
    })
    Revision = $resource('/rest/program/:bsn/revision/:rev',{bsn:'@bsn', rev:'@version.base'}, {
      'get': {method:'GET', params: {}},
      'query': {method: 'GET', params:{query:@query,start:@start,limit:PAGE_SIZE}, isArray:true}
      'rescan': {method:'OPTION', params:{cmd:'rescan'}}
      'master': {method:'OPTION', params:{cmd:'master'}}
    })
    User = $resource('/rest/login',{}, {
      'login': {method: 'GET', params:{email:@email,assertion:@assertion}},
      'logout': {method: 'GET', params:{}},
    })
    
JPMApp = angular.module( 'jpm', ['ngResource'] ).config(routes).run( activate )


converter = new Markdown.Converter().makeHtml;

JPMApp.directive('markdown', () -> {
     restrict: 			'C', 
     require: 			'ngModel',
     transclude: 		true,
     replace: 			true,
     scope: 			{ model:'=ngModel', editable:'=editable', change:'@change' },
     template: 			"""
	<div>
		<textarea ng-show="editable" ng-model="model" ng-transclude></textarea>
		<div class="markdown-body" ng-transclude></div>
	</div>
	""",
     link: 				(scope, element, attrs, ngModel) ->
          markdown = element.find("div")          
          textarea = element.find("textarea")
          textarea.bind('keyup', (() -> element.parent().scope().$apply(scope.change))  )          
          scope.$watch( 'model', () -> markdown.html( converter( scope.model || 'TBD')))
     })
