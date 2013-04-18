
var app = angular.module('surveys', ['ui'])
function Surveys($scope, $http) {
    $scope.problem = false;

    $scope.signUp = function() {

        $http.post('/time/users/' + $scope.username + '/signup?displayName=' + $scope.displayName, $scope.password).success(function(user) {
            $scope.user = user;
            console.log("SIGN UP", user);
            $http.defaults.headers.common['Authorization'] = 'Basic ' + Base64.encode($scope.username + ':' + $scope.password);
        });
    };

    $scope.login = function() {
        $http.defaults.headers.common['Authorization'] = 'Basic ' + Base64.encode($scope.username + ':' + $scope.password);
        $http.get('/time/whoami').success(function(user) {
            $scope.user = user;
            console.log("LOGIN", user);
        });
    }
}

function ServerLog($scope) {

}
