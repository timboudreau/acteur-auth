function Surveys($scope, $http, lookingAt, urls) {
    $scope.problem = false;
    function load() {
        $scope.loading = true;
        $http.get(urls.userPath(lookingAt.name, 'surveys')).success(function(surveys) {
            $scope.loading = false;
            $scope.surveys = surveys;
        }).error(function() {
            $scope.loading = false;
        })
    }
    load();
}
