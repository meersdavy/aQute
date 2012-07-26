# 
# Main program for the jpm.html page. 


PAGE_SIZE 	= 10				# Default page size
Program 	= undefined		# shared resource manager for programs
Revision 	= undefined		# shared resource manager for revisions
User 	    = undefined		# User management

class Log
   log   : []
   report: (title, error, severity ) -> this.log.push( { title: title, error: error, severity: severity } )
   clear : -> this.log = []
   hasErrors: -> this.log.length


window.JPM = ($scope, $location, $routeParams) ->
    window.title = "JPM"
    assertion = null
    auth = StateMachine.create({
       initial: 'init',
       events: [
          { name: 'done',         from: 'init',           to: 'viewing' },
          { name: 'done',         from: 'viewing',        to: 'viewing' },
          { name: 'done',         from: 'authenticated',  to: 'authenticated' },
          { name: 'collect',      from: 'viewing',        to: 'collecting' },
          { name: 'verify',       from: 'collecting',     to: 'verifying' },
          { name: 'login',        from: 'init', 		  to: 'authenticated' },
          { name: 'login',        from: 'verifying',      to: 'authenticated' },
          
          { name: 'logout',       from: 'authenticated',  to: 'viewing' },
          { name: 'failed',       from: ['collecting', 'verifying'], to: 'viewing' },
          { name: 'cancel',       from: ['collecting', 'verifying'], to: 'viewing' },
       ],
       callbacks: {
          oncollecting: () ->
              navigator.id.get( (assertion) -> 
                  if assertion 
                      $scope.assertion = assertion
                      auth.verify()
                  else
                      auth.failed()
              )

          onverifying: () ->
              $scope.user = User.login({assertion:$scope.assertion,email:''}, 
                  (() -> auth.login()), (() -> auth.failed()) )
                                          
          onenterauthenticated:  () -> 
              sessionStorage.user=angular.toJson($scope.user)
          
          onleaveauthenticated: () -> 
              $scope.user = null
              sessionStorage.user=null
              navigator.id.logout()

       }
    });
    $scope.auth = auth
    
    #
    #  User/login/logout
    #
    if sessionStorage.user
        window.console.log("auto login from previous session = " + sessionStorage.user )
        $scope.user = angular.fromJson( sessionStorage.user)
        if ( !($scope.user && $scope.user.email)) 
            $scope.user = null;
        else 
            $scope.auth.login()
            
    $scope.auth.done()
    
    # called from window when needs to login
    $scope.escape   = (s) -> encodeURIComponent(s)
    $scope.auth.permission = (action) -> $scope.user # && user.roles.indexOf(action) > = 0 

    # error handling
    $scope.error = new Log
	
#
# Searching
#
    
SearchCtl = ($scope, $location, $routeParams ) ->
    $scope.start 	 = $routeParams.start || 0
    $scope.query     = $routeParams.q || ""
    if ($scope.query )
        $scope.programs = Program.query({query:$scope.query,start:$scope.start})
    
    $scope.search 			= -> 
        $scope.start = 0 unless 0 <= $scope.start <= 100000;
        $location.search("q=#{escape($scope.query)}&start=#{$scope.start}")
        

    $scope.next             = ->
        $location.search("q=#{escape($scope.query)}&start=#{$scope.start+PAGE_SIZE}")
        
    $scope.prev             = ->
        s = $scope.start - PAGE_SIZE;
        $location.search("q=#{escape($scope.query)}&start=#{s > 0 ? s : 0}")

#
# Controller for the program fragment
# See jpm/

ProgramCtl = ($scope, $location, $routeParams ) ->
    $scope.program = Program.get($routeParams)
    if ( $routeParams.rev )
        $scope.revision = Revision.get($routeParams)
    else
        $scope.revision = null
    state = StateMachine.create({
       initial: 'viewing',
       events: [
          { name: 'edit',         from: 'viewing',        to: 'editing' },
          { name: 'changed',      from: 'editing',        to: 'dirty' },
          { name: 'changed',      from: 'dirty',          to: 'dirty' },
          { name: 'save',         from: 'dirty',          to: 'saving'  },
          { name: 'cancel',       from: 'editing',        to: 'viewing'  },
          { name: 'cancel',       from: 'dirty',          to: 'aborting'  },
          { name: 'cancel',       from: 'saving',         to: 'aborting' },
          { name: 'restored',     from: 'aborting',       to: 'viewing' },
          { name: 'fail',         from: 'aborting',       to: 'viewing' },
          { name: 'fail',         from: 'saving',         to: 'aborting' },
          { name: 'fail',         from: 'aborting',       to: 'viewing' },
          { name: 'saved',        from: 'saving',         to: 'viewing' },
          
          { name: 'rescan',       from: 'viewing',        to: 'command' },
          { name: 'master',       from: 'viewing',        to: 'command' },
          { name: 'fail',         from: 'command',        to: 'viewing' },
          { name: 'done',         from: 'command',        to: 'viewing' },
          { name: 'done',         from: 'failed',         to: 'viewing' },
       ],
       callbacks: {
          onediting: () -> $scope.snapshot = angular.toJson($scope.program)
          onsaving:    () -> 
              $scope.program.$save( 
                  [], 
                  () ->state.saved(), 
                  (reason) ->
                      $scope.error.report($scope.program._id, "saving")
                      state.fail()
              )
          onaborting:  () -> 
              $scope.program = Program.get( $routeParams, 
                  () ->state.restored(),
                  (what) -> 
                      $scope.error.report($scope.program._id, "aborting")
                      state.fail(); 
              )
          onrescan:    () -> $scope.revision.$rescan((->state.done()), (->state.fail()))
          onmaster:    () -> $scope.revision.$master((->state.done()), (->state.fail()))
       }
    })
    $scope.state = state
    $scope.icon   = (i) -> i || '/img/default-icon.png'
    $scope.buttons = -> ['rescan', 'master', 'cancel']
