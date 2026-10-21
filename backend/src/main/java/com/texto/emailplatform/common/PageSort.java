package com.texto.emailplatform.common;

import com.texto.emailplatform.common.exception.ApiException;
import java.util.Set;
import org.springframework.data.domain.Sort;

/** Only allow known persistent fields; append a stable key for page boundaries. */
public final class PageSort {
    private PageSort() {}
    public static Sort of(String field, String direction, Set<String> allowed) {
        if (field == null || !allowed.contains(field) || direction == null ||
                !(direction.equalsIgnoreCase("asc") || direction.equalsIgnoreCase("desc"))) {
            throw new ApiException(400, "INVALID_SORT", "Choose a supported sort column and asc or desc direction");
        }
        return Sort.by(Sort.Direction.fromString(direction), field).and(Sort.by("id"));
    }
}
