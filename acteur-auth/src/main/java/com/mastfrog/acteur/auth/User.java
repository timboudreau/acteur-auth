package com.mastfrog.acteur.auth;

import com.google.common.base.Optional;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

/**
 *
 * @author tim
 */
public interface User<IdType> extends SimpleUser<IdType> {
    public List<String> names();
    public String name();
    public int version();
    public IdType id();
    public String idAsString();
    public List<IdType> authorizes();
    public String displayName();
    public String hashedPassword();
    public Set<String> authInfoNames();
    public Optional<OAuthInfo> authInfo(String serviceCode);
    
    public interface OAuthInfo {
        public String slug();
        public ZonedDateTime lastModified();
        public Optional<String> savedToken();
        public String service();
    }
}
