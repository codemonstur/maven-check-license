package checklicense.model;

import org.apache.maven.model.License;

import java.util.Objects;
import java.util.regex.Pattern;

import static checklicense.model.MatchField.name;
import static checklicense.model.MatchType.regex;
import static checklicense.model.MatchType.equal;

public final class Rule {

    private final String rule;
    private final MatchField field;
    private final MatchType type;

    private final String value;
    private final Pattern pattern;

    public Rule(final String rule) {
        this.rule = rule;
        final var parts = rule.split(":", 3);
        this.field = MatchField.valueOf(parts[0]);
        this.type = MatchType.valueOf(parts[1]);
        this.value = type == equal ? parts[2] : null;
        this.pattern = type == regex ? Pattern.compile(parts[2]) : null;
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
