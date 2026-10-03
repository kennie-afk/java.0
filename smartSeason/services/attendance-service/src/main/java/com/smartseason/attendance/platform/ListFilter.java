package com.smartseason.attendance.platform;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class ListFilter {

    private static final Set<String> RESERVED = Set.of("page", "size", "sort", "q");
    private static final int MAX_QUERY = 100;

    private ListFilter() {
    }

    public static boolean isEmpty(Map<String, String> params) {
        if (params == null) {
            return true;
        }
        boolean onlyPaging = params.keySet().stream().allMatch(RESERVED::contains);
        String q = params.get("q");
        return onlyPaging && (q == null || q.isBlank());
    }

    public static <T> Specification<T> of(UUID tenantId, Map<String, String> params,
                                          Map<String, Class<?>> filterable, List<String> searchable) {
        List<Object[]> exact = new ArrayList<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            if (RESERVED.contains(key)) {
                continue;
            }
            Class<?> type = filterable.get(key);
            if (type == null) {
                throw new DomainRuleException("Cannot filter by '" + key + "'. Filterable: "
                        + String.join(", ", filterable.keySet().stream().sorted().toList()));
            }
            exact.add(new Object[] {key, parse(key, type, entry.getValue())});
        }
        String q = params.get("q");
        String needle = q == null ? "" : q.strip();
        if (needle.length() > MAX_QUERY) {
            throw new DomainRuleException("Search text is limited to " + MAX_QUERY + " characters");
        }
        String like = "%" + needle.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";

        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            all.add(cb.equal(root.get("tenantId"), tenantId));
            for (Object[] pair : exact) {
                all.add(cb.equal(root.get((String) pair[0]), pair[1]));
            }
            if (!needle.isEmpty() && !searchable.isEmpty()) {
                List<Predicate> any = new ArrayList<>();
                for (String column : searchable) {
                    any.add(cb.like(cb.lower(root.<String>get(column)), like, '\\'));
                }
                all.add(cb.or(any.toArray(new Predicate[0])));
            }
            return cb.and(all.toArray(new Predicate[0]));
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parse(String key, Class<?> type, String raw) {
        String text = raw == null ? "" : raw.strip();
        try {
            if (type == UUID.class) {
                return UUID.fromString(text);
            }
            if (type == Boolean.class) {
                if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException(text);
                }
                return Boolean.valueOf(text);
            }
            if (type.isEnum()) {
                return Enum.valueOf((Class<Enum>) type, text.toUpperCase(Locale.ROOT));
            }
            return text;
        } catch (IllegalArgumentException ex) {
            throw new DomainRuleException("'" + text + "' is not a valid value for " + key);
        }
    }
}
