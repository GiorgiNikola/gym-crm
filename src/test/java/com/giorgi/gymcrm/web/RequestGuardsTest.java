package com.giorgi.gymcrm.web;

import com.giorgi.gymcrm.exception.ForbiddenOperationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestGuardsTest {

    @Test
    @DisplayName("a user acting on their own data passes")
    void allowsOwnUsername() {
        Credentials credentials = new Credentials("John.Smith", "aX7kQ2mN9p");

        Assertions.assertDoesNotThrow(() -> RequestGuards.requireSelf("John.Smith", credentials));
    }

    @Test
    @DisplayName("a user acting on somebody else is stopped")
    void rejectsOtherUsername() {
        Credentials credentials = new Credentials("Sarah.Miller", "bY8lR3nO0q");

        Assertions.assertThrows(ForbiddenOperationException.class,
                () -> RequestGuards.requireSelf("John.Smith", credentials));
    }
}
