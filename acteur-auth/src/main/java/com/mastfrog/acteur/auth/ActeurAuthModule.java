package com.mastfrog.acteur.auth;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;

/**
 * Sets up the necessary bindings for authentication
 *
 * @author Tim Boudreau
 */
public class ActeurAuthModule<T extends UserFactory<?>> extends AbstractModule {

    private static final class UserFactoryTL extends TypeLiteral<UserFactory<?>> {
    }
    private final Class<T> userFactoryType;

    public ActeurAuthModule(Class<T> userFactoryType) {
        this.userFactoryType = userFactoryType;
    }

    @Override
    protected void configure() {
        // This looks like insanity, but it actually goes to the nature of Google's
        // Key - Guice is really all about Keys.  As far as it is concerned,
        // UserFactory is a different type than UserFactory<?> which is different
        // from MyUserFactory even if that is bound.  So binding UserFactory, 
        // UserFactory<?> and MyUserFactory gets you three singletons :-)
        // 
        // This contortion ensures there is really only one, but lets instances
        // of untyped UserFactory be injected, and allows access to the concrete
        // implementation type if desired
        bind(UserFactory.class).to(userFactoryType).in(Scopes.SINGLETON);
        bind(new UserFactoryTL()).toProvider(new GenericProvider(binder().getProvider(UserFactory.class)));
    }

    private class GenericProvider implements Provider<UserFactory<?>> {

        private final Provider<UserFactory> p;

        public GenericProvider(Provider<UserFactory> p) {
            this.p = p;
        }

        @Override
        public T get() {
            return userFactoryType.cast(p.get());
        }

    }
}
