//var app = angular.module('surveys');

function Surveys($scope, $http) {
    $scope.problem = false;

    console.log("URL: ", window.location.pathname.split('/'));
    var user = window.location.pathname.split('/')[2];
    console.log("USER IS " + user);
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
