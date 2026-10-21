package org.ymxr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.ymxs.tool.Tool;

/**
 * A multi file: several tune files in one, a name each, which
 * {@code ymxr-sndh} reads as a set of subtunes (doc/BINARIES.md 0). A
 * player reads a tune file (SPEC.md 3.3) and never this: a multi file
 * passes tune files between tools, so that a tool reads one input and
 * writes one output.
 *
 * <p>{@code ymxr-multi tune.ymxr more.ymxr [-nNAME]... [-mN] [-copies[S]]}
 * writes one on standard output, each table packed again through the ring
 * chosen for the set (tools.md 4.7), and {@code ymxs-to-ymxr} writes one
 * where the structure it reads has several tunes.
 */
final class Multi {

    /** The four bytes a multi file opens with. */
    static final byte[] MAGIC = {'Y', 'M', 'X', 'M'};

    /** The version of a multi file, its tune files' (SPEC.md 3.3.5). */
    static final int VERSION = Tune.VERSION;

    static final int COUNT_AT = 6;
    static final int INDEX_AT = 8;

    /** An entry: where the tune file begins, and its bytes. */
    private static final int ENTRY = 8;

    /** The most tunes in one, the subtunes an SNDH file numbers. */
    static final int MOST = Sndh.MAX_SUBTUNES;

    /** What a multi file has in it: the tune files, and the name of each,
     *  both in the file's order. */
    record Read(List<byte[]> tunes, List<String> names) {
    }

    private Multi() {
    }

    /** Whether {@code file} opens as a multi file. */
    static boolean is(byte[] file) {
        return file.length >= INDEX_AT && Arrays.equals(Arrays.copyOf(file, 4), MAGIC);
    }

    /** The multi file of these tune files, named one for one.
     *
     * @throws IllegalArgumentException where the two lists differ in
     *     length, where there are no tunes or more than {@link #MOST}, or
     *     where a file is not a tune file of this version
     */
    static byte[] of(List<byte[]> tunes, List<String> names) {
        if (tunes.size() != names.size()) {
            throw new IllegalArgumentException(names.size() + " names for " + tunes.size()
                    + " tunes");
        }
        if (tunes.isEmpty()) {
            throw new IllegalArgumentException("no tunes: a multi file has one at least");
        }
        if (tunes.size() > MOST) {
            throw new IllegalArgumentException(tunes.size() + " tunes, and a multi file has "
                    + MOST + " at most");
        }
        List<byte[]> said = new ArrayList<>();
        int text = 0;
        for (int i = 0; i < tunes.size(); i++) {
            TuneFile.read(tunes.get(i));
            byte[] name = names.get(i).getBytes(StandardCharsets.UTF_8);
            said.add(name);
            text += name.length + 1;
        }
        int at = Tune.align(INDEX_AT + ENTRY * tunes.size() + text);
        int bytes = at;
        for (byte[] tune : tunes) {
            bytes = Tune.align(bytes + tune.length);
        }
        byte[] file = new byte[bytes];
        System.arraycopy(MAGIC, 0, file, 0, 4);
        Tune.putWord(file, 4, Sndh.binds(tunes));
        Tune.putWord(file, COUNT_AT, tunes.size());
        int name = INDEX_AT + ENTRY * tunes.size();
        for (int i = 0; i < tunes.size(); i++) {
            Tune.putLong(file, INDEX_AT + ENTRY * i, at);
            Tune.putLong(file, INDEX_AT + ENTRY * i + 4, tunes.get(i).length);
            System.arraycopy(said.get(i), 0, file, name, said.get(i).length);
            name += said.get(i).length + 1;
            System.arraycopy(tunes.get(i), 0, file, at, tunes.get(i).length);
            at = Tune.align(at + tunes.get(i).length);
        }
        return file;
    }

    /** The tune files and names {@code file} has in it.
     *
     * @throws IllegalArgumentException where it is not a multi file of
     *     this version, or an entry is outside it
     */
    static Read read(byte[] file) {
        if (!is(file)) {
            throw new IllegalArgumentException("not a YMXM file");
        }
        int version = Tune.getWord(file, 4);
        if (version >= Tune.VERSION_DTX2 && version <= Tune.VERSION_DTX2_LAST) {
            throw new IllegalArgumentException("version " + version + " has DTX2 tables,"
                    + " and this reads " + VERSION + ": write the tunes again with"
                    + " ym-to-ymxr or ymxs-to-ymxr");
        }
        if (version != VERSION) {
            throw new IllegalArgumentException("version " + version + " is not " + VERSION);
        }
        int count = Tune.getWord(file, COUNT_AT);
        if (count < 1 || count > MOST) {
            throw new IllegalArgumentException(count + " tunes, and a multi file has 1 to "
                    + MOST);
        }
        int name = INDEX_AT + ENTRY * count;
        if (name > file.length) {
            throw new IllegalArgumentException("the entries of " + count
                    + " tunes end past the file's " + file.length + " bytes");
        }
        List<byte[]> tunes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int at = Tune.getLong(file, INDEX_AT + ENTRY * i);
            int bytes = Tune.getLong(file, INDEX_AT + ENTRY * i + 4);
            if (at < 0 || bytes < 0 || at + bytes > file.length) {
                throw new IllegalArgumentException("tune " + (i + 1) + " is at " + at
                        + " for " + bytes + " bytes, and the file has " + file.length);
            }
            tunes.add(Arrays.copyOfRange(file, at, at + bytes));
            int end = name;
            while (end < file.length && file[end] != 0) {
                end++;
            }
            names.add(new String(file, name, end - name, StandardCharsets.UTF_8));
            name = end + 1;
        }
        return new Read(tunes, names);
    }

    /** The stem of a file's name, which names its tune. */
    static String stem(String file) {
        String name = Path.of(file).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * {@code ymxr-multi}: the tune files named, as one multi file on
     * standard output, each table packed again through the ring chosen for
     * the set, or through the ring {@code -mN} names, and with copies where
     * they pay under {@code -copies[S]} (tools.md 4.2, 4.3, 4.7). Each tune
     * is named by its file unless {@code -nNAME} names it, the names in the
     * order the files are named.
     */
    public static void main(String[] args) {
        List<String> flags = new ArrayList<>(Arrays.asList(args));
        Tool tool = Tool.of("ymxr-multi", flags, "-n", "-m", "-copies");
        List<String> named = new ArrayList<>();
        List<String> files = new ArrayList<>();
        List<String> packing = new ArrayList<>();
        for (String flag : flags) {
            if (flag.startsWith("-n")) {
                named.add(flag.substring(2));
            } else if (flag.startsWith("-m") || flag.startsWith("-copies")) {
                packing.add(flag);
            } else if (flag.startsWith("-")) {
                throw tool.usage("not a flag of the tool: " + flag);
            } else {
                files.add(flag);
            }
        }
        // The call is read before a file is, so a flag that is not a
        // number is an exit of 2 and every file is left unread.
        Ymxs.Packing packed = Ymxs.Packing.of(tool, packing);
        if (files.isEmpty()) {
            throw tool.usage("ymxr-multi tune.ymxr [more.ymxr ...] [-nNAME]... [-mN]"
                    + " [-copies[S]]");
        }
        if (named.size() > files.size()) {
            throw tool.usage(named.size() + " names for " + files.size() + " tune files");
        }
        List<byte[]> tunes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            try {
                tunes.add(Files.readAllBytes(Path.of(files.get(i))));
            } catch (IOException failed) {
                throw tool.wrong(Tool.FAILED, "cannot read " + files.get(i) + ": "
                        + failed.getMessage());
            }
            // -n names a tune, then the name the tune file records, then
            // the file it was read from
            String recorded = Tune.name(tunes.get(tunes.size() - 1));
            names.add(i < named.size() ? named.get(i)
                    : !recorded.isEmpty() ? recorded : stem(files.get(i)));
        }
        if (tunes.size() > MOST) {
            // before the packing, which would run for every tune first
            throw tool.wrong(Tool.WRONG, tunes.size() + " tunes, and a multi file has " + MOST
                    + " at most");
        }
        Report report = new Report(tool.reports());
        byte[] file;
        try {
            List<Tune.Written> set = SetRings.ofFiles(tunes, packed.packers(), packed.ring(),
                    report);
            tunes.clear();
            for (Tune.Written tune : set) {
                Tune.packed(report, tune, packed.ring() == Tune.CHOOSE ? Tune.CHOSE_FOR_SET : "");
                tunes.add(tune.file());
            }
            SetRings.say(report, set);
            file = of(tunes, names);
        } catch (IllegalArgumentException wrong) {
            throw tool.wrong(Tool.WRONG, String.valueOf(wrong.getMessage()));
        }
        for (int i = 0; i < tunes.size(); i++) {
            tool.report(names.get(i) + ": " + Report.count(tunes.get(i).length, "byte", "bytes"));
        }
        tool.report(Report.count(tunes.size(), "tune", "tunes") + ", "
                + Report.count(file.length, "byte", "bytes"));
        Out.write(tool, file);
    }
}
