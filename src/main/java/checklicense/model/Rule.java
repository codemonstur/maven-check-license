package checklicense.model;

import org.apache.maven.model.License;

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static checklicense.model.MatchField.name;
import static checklicense.model.MatchType.equal;
import static checklicense.model.MatchType.regex;
import static java.util.stream.Collectors.joining;

public final class Rule {

    private final String rule;
    private final MatchField field;
    private final MatchType type;

    private final String value;
    private final Pattern pattern;

    public Rule(final String rule) {
        this.rule = rule;
        final var parts = rule.split(":", 3);
        if (parts.length != 3)
            throw new IllegalArgumentException("expected the format <field>:<type>:<value>");
        this.field = parseField(parts[0]);
        this.type = parseType(parts[1]);
        this.value = type == equal ? parts[2] : null;
        this.pattern = type == regex ? compile(parts[2]) : null;
    }

    private static MatchField parseField(final String field) {
        try {
            return MatchField.valueOf(field);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown field '" + field + "', expected one of ["
                    + values(MatchField.values()) + "]");
        }
    }

    private static MatchType parseType(final String type) {
        try {
            return MatchType.valueOf(type);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown type '" + type + "', expected one of ["
                    + values(MatchType.values()) + "]");
        }
    }

    private static Pattern compile(final String regex) {
        try {
            return Pattern.compile(regex);
        } catch (final PatternSyntaxException e) {
            throw new IllegalArgumentException("invalid regex '" + regex + "', " + e.getDescription());
        }
    }

    private static String values(final Enum<?>... constants) {
        return Arrays.stream(constants).map(Enum::name).collect(joining(", "));
    }

    public boolean matches(final License license) {
        final var data = field == name ? license.getName() : license.getUrl();

        if (data == null) return false;
        if (type == equal) return value.equals(data);
        if (type == regex) return pattern.matcher(data).matches();

        return false;
    }

    @Override
    public String toString() {
        return rule;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(rule);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Rule rule1 = (Rule) o;
        return Objects.equals(rule, rule1.rule);
    }

}
