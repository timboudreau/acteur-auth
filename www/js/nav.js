

function NavItem ( path, name, selected ) {
    this.path = path;
    this.name = name;
    this.class = selected ? 'topNavLink topNavSelected' : 'topNavLink';
}

function NavController ( $scope ) {
    var isSecure = false;
    var u = "/blog/latest/read";
    if (typeof document !== 'undefined') {
        u = document.URL;
        isSecure = /.*\/secure\/.*/.test ( u );
    }

    var items = [];
    var isBlog = /.*\/read[\/#]?$/.test ( u ) || /.*\/edit[\/#]?$/.test ( u );

    var rex = /.*\/(.*)\/read/; //XXX test other
    var which = rex.test ( u ) ? rex.exec ( u )[1] : null;

    if (isSecure) {
        items.push ( new NavItem ( '/blog/secure/list', "Blog", true ) );
        items.push ( new NavItem ( '/blog/secure/new', "New Blog" ) );
        items.push ( new NavItem ( '/blog/secure/manage', "Manage" ) );
    } else {
        items.push ( new NavItem ( '/blog/list', "Blog", true ) );
    }
    if (which && isSecure && isBlog) {
        items.push ( new NavItem ( '/blog/secure/' + which + '/edit', 'Edit' ) );
        items.push ( new NavItem ( '/blog/secure/' + which + '/comments', "Manage Comments" ) );
    }

    items.push ( new NavItem ( '/code', 'Code' ) );
    items.push ( new NavItem ( 'http://timboudreau.smugmug.com/', 'Photos' ) );
    items.push ( new NavItem ( 'http://www.reverbnation.com/timboudreau', 'Music' ) );
    items.push ( new NavItem ( '/builds/', 'Builds' ) );

    $scope.navigation = items;
}
if (typeof exports != 'undefined') {
    exports.NavController = NavController;
}
