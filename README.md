Acteur Authentication
---------------------

Provides a small framework for dealing with authentication in
[acteur](../acteur/), including OAuth adapters for Facebook,
Twitter, LinkedIn and Google.  Provides URL handlers for the
various OAuth callbacks these systems require.  You simply
implement a ``UserFactory`` which handles CRUD operations for
users, stores credentials and so forth.

At this point, the code here should be considered experimental.

See [the javadoc](http://timboudreau.com/builds) for more info.

