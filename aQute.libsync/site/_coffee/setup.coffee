routes 	= ($routeProvider) -> 
     $routeProvider.
       when('/p/:bsn/r/:rev',	{ templateUrl: '/jpm/program.htm', 	controller: ProgramCtl }).
       when('/p/:bsn',			{ templateUrl: '/jpm/program.htm', 	controller: ProgramCtl }).
       when('/',	  			{ templateUrl: '/jpm/search.htm', 	controller: SearchCtl }).
       otherwise( 		    	{ redirectTo: '/' } )


activate = ( $resource, $location, $routeParams ) ->
    Program = $resource('/rest/program/:bsn',{}, {
      'get': {method:'GET', params: {}},
      'query': {method: 'GET', params:{query:@query,start:@start,limit:PAGE_SIZE}, isArray:true}
    })
    Revision = $resource('/rest/program/:bsn/revision/:rev',{}, {
      'get': {method:'GET', params: {}}
    })
    
angular.module( 'jpm', ['ngResource'] ).config(routes).run( activate )
