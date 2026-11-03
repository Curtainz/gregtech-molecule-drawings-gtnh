package com.rubenverg.moldraw.core.pack;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;

public record SemVer(BigInteger major, BigInteger minor, BigInteger patch, List<String> pre)
    implements Comparable<SemVer> {

    private static final Pattern VERSION = Pattern
        .compile("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z.-]+))?");

    public SemVer {
        pre = List.copyOf(pre);
    }

    public static SemVer parse(String value) {
        var m = VERSION.matcher(value);
        if (!m.matches()) throw new PackException("Invalid SemVer " + value);
        List<String> pre = m.group(4) == null ? List.of()
            : List.of(
                m.group(4)
                    .split("\\.", -1));
        for (String term : pre)
            if (term.isEmpty() || (term.matches("[0-9]+") && term.length() > 1 && term.startsWith("0")))
                throw new PackException("Invalid prerelease " + value);
        return new SemVer(new BigInteger(m.group(1)), new BigInteger(m.group(2)), new BigInteger(m.group(3)), pre);
    }

    public int compareTo(SemVer b) {
        int c = major.compareTo(b.major);
        if (c != 0) return c;
        c = minor.compareTo(b.minor);
        if (c != 0) return c;
        c = patch.compareTo(b.patch);
        if (c != 0) return c;
        if (pre.isEmpty() || b.pre.isEmpty()) return pre.isEmpty() ? (b.pre.isEmpty() ? 0 : 1) : -1;
        for (int i = 0; i < Math.min(pre.size(), b.pre.size()); i++) {
            String a = pre.get(i), v = b.pre.get(i);
            boolean an = a.matches("[0-9]+"), bn = v.matches("[0-9]+");
            c = an && bn ? new BigInteger(a).compareTo(new BigInteger(v)) : an != bn ? (an ? -1 : 1) : a.compareTo(v);
            if (c != 0) return c;
        }
        return Integer.compare(pre.size(), b.pre.size());
    }
}
