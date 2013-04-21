function Surveys($scope, $http) {
    $scope.problem = false;

    var user = window.location.pathname.split('/')[2];
    function load() {
        $scope.loading = true;
        $http.get('/time/users/' + user + '/surveys').success(function(surveys) {
            $scope.loading = false;
            $scope.surveys = surveys;
        }).error(function() {
            $scope.loading = false;
        })
    }
    load();
}
