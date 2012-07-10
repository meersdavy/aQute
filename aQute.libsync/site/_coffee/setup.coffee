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
      'get': {method:'GET', params: {}},
      'rescan': {method:'OPTION', params:{cmd:'rescan'}}
    })
    User = $resource('/rest/login',{}, {
      'login': {method: 'GET', params:{email:@email,assertion:@assertion}},
      'logout': {method: 'GET', params:{}},
    })
    
JPMApp = angular.module( 'jpm', ['ngResource'] ).config(routes).run( activate )

f = () ->
    alert(1)
    return (scope,element,attrs) ->
        alert(2) 
        editor=new EpicEditor({container: element.id, file: {defaultContent: 'edit me ...', autoSave: 1000}}).load()
        scope.$watch(attrs.ngModel, (value) -> editor.importFile('x', value))
        editor.on('save', () -> scope.program.description = editor.exportFile(); scope.$apply() );
        
JPMApp.directive('ee', () -> {
     restrict: 'A', 
     require: 'ngModel', 
     link: (scope, element, attrs, ngModel) ->
          editor = new EpicEditor({container: element.id, file: {autoSave:1000} }).load()
          editor.preview();
          read = () -> ngModel.$setViewValue(editor.exportFile())
          ngModel.$render = () -> editor.importFile('x', ngModel.$viewValue || '')
          editor.on('save', () -> scope.$apply(read) )
          skip = true
          read(); 
     })
      
      

