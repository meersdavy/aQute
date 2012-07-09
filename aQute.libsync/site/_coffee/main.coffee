# 
# Main program for the jpm.html page. 


PAGE_SIZE 	= 10				# Default page size
Program 	= undefined		# shared resource manager for programs
Revision 	= undefined		# shared resource manager for revisions

#
# Controller for the search fragment
#
SearchCtl = ($scope, $location, $routeParams ) ->
    $scope.start 	 = $routeParams.start || 0
    $scope.query     = $routeParams.q || ""
    if ($scope.query )
        $scope.programs = Program.query({query:$scope.query,start:$scope.start})
    
    $scope.canSearch= -> $scope.query
    
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
    $scope.program 		= Program.get( $routeParams )
    if ( $routeParams.rev )
        $scope.revision = Revision.get($routeParams)
    else
        $scope.revision = null
    $scope.type   = (t) -> 'staged'
    $scope.date   = (t) -> new Date(t).toString()
    $scope.icon   = (i) -> i || '/img/default-icon.png'
    $scope.rescan = () -> $scope.revision.$rescan({bsn:$scope.revision.bsn,rev:$scope.revision.version.base}); 
