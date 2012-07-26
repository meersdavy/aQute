#
# Controller for the program fragment
# See jpm/

AdminCtl = ($scope, $location, $routeParams, $http ) ->
    $scope.state = StateMachine.create({
       initial: 'viewing',
       events: [
          { name: 'reindex',      from: 'viewing',        to: 'command' },
          { name: 'fail',         from: 'command',        to: 'viewing' },
          { name: 'done',         from: 'command',        to: 'viewing' },
          { name: 'done',         from: 'failed',         to: 'viewing' }
       ],
       callbacks: {
          onreindex:    () -> $http({method:'OPTION', url:'/rest/obrReindex'}).success(()->state.done()).error(()-> alert("fail"); state.fail())
       }
    })
