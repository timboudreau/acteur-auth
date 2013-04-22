function Surveys($scope, $http, lookingAt) {
    $scope.problem = false;
    function load() {
        $scope.loading = true;
        $http.get(API_BASE + 'users/' + lookingAt.name + '/surveys').success(function(surveys) {
            $scope.loading = false;
            $scope.surveys = surveys;
        }).error(function() {
            $scope.loading = false;
        })
    }
    load();
}
