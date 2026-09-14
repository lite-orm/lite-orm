package io.github.kervix.transaction;

import io.github.kervix.api.TransactionDomain;
import io.github.kervix.api.TransactionDomainGuard;
import io.github.kervix.api.TransactionException;

import java.util.Objects;

/**
 * Thread-confined transaction-domain scope shared explicitly by related simple factories.
 */
public final class SimpleTransactionDomainGuard implements TransactionDomainGuard {

    private final ThreadLocal<Binding> currentBinding = new ThreadLocal<>();

    @Override
    public void verify(TransactionDomain domain) {
        Objects.requireNonNull(domain, "domain");
        Binding current = currentBinding.get();
        if (current != null && !current.domain().equals(domain)) {
            throw mismatch(current.domain(), domain);
        }
    }

    Binding bind(TransactionDomain domain) {
        verify(domain);
        if (currentBinding.get() != null) {
            throw new TransactionException(
                TransactionException.Type.BEGIN_FAILED,
                "A transaction is already active for domain '" + domain.key() + "'"
            );
        }
        Binding binding = new Binding(domain);
        currentBinding.set(binding);
        return binding;
    }

    void clear(Binding binding) {
        if (currentBinding.get() == binding) {
            currentBinding.remove();
        }
    }

    private TransactionException mismatch(TransactionDomain active, TransactionDomain requested) {
        return new TransactionException(
            TransactionException.Type.DOMAIN_MISMATCH,
            "Active transaction domain '" + active.key()
                + "' cannot access executor domain '" + requested.key() + "'"
        );
    }

    record Binding(TransactionDomain domain) {
    }
}
