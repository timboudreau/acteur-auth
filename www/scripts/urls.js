(function() {
    'use strict';

    angular.module('urls', []).service('urls', function() {
        var self = this;
        this.displayNameCookie = "dn";
        this.userBase = 'users';
        this.apiBase = '/time/';
        this.cookieNames = ['gg', 'fb', 'ba'];

        this.path = function() {
            var args = [];
            for (var i=0; i < arguments.length; i++) {
                args.push(arguments[i])
            }
            return self.apiBase + args.join('/');
        }

        this.userPath = function(userName, els) {
            return self.path('users') + '/' + userName + '/' 
                    + (els ? (typeof els === 'string' ? els : els.join('/')) : '');
        }
        
        this.userHome = function(userName) {
            return '/users/' + userName + '/'
        }
        
        this.__defineGetter__('currentUser', function() {
            var un = window.location.pathname.split('/');
            return un && un.length > 2 ? un[2] : null;
        });
    });

})();
