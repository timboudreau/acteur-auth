// Generated by com.dv.sourcetreetool.impl.App
open module com.mastfrog.acteur.facebook.auth {
    exports com.mastfrog.acteur.facebook.auth;

    // derived from com.google.http-client/google-http-client-jackson2-1.41.7 in com/google/http-client/google-http-client-jackson2/1.41.7/google-http-client-jackson2-1.41.7.pom
    requires transitive com.google.api.client.json.jackson2;

    // Sibling com.mastfrog/acteur-auth-3.0.0-dev
    requires com.mastfrog.acteur.auth;

    // Transitive detected by source scan
    requires com.mastfrog.acteur.deprecated;

    // Sibling com.mastfrog/url-3.0.0-dev
    requires com.mastfrog.url;

    // derived from com.restfb/restfb-2022.4.0 in com/restfb/restfb/2022.4.0/restfb-2022.4.0.pom
    requires transitive restfb.2022.4.0;

    // derived from org.scribe/scribe-1.3.7 in org/scribe/scribe/1.3.7/scribe-1.3.7.pom
    requires transitive scribe.1.3.7;

}
