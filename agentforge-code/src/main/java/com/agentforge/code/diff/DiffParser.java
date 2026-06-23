package com.agentforge.code.diff;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified Diff 解析器。
 *
 * <p>解析标准 git diff 格式，把多文件 diff 拆分为多个 {@link DiffFile}：
 * <pre>
 * diff --git a/src/Foo.java b/src/Foo.java
 * index 111..222 100644
 * --- a/src/Foo.java
 * +++ b/src/Foo.java
 * @@ -10,3 +10,4 @@ context
 *  context line
 * -deleted line
 * +added line
 * </pre>
 *
 * <p>文件分类按扩展名：.java→JAVA, .sql→SQL, .xml→XML, .yml/.properties/.json→CONFIG。
 * 变更类型由 diff header 的 {@code new file mode}/{@code deleted file mode} 判定。
 */
@Slf4j
@Component
public class DiffParser {

    /** 解析整个 diff 文本。 */
    public List<DiffFile> parse(String diffText) {
        List<DiffFile> files = new ArrayList<>();
        if (diffText == null || diffText.isBlank()) {
            return files;
        }
        String[] lines = diffText.split("\n", -1);
        DiffFile current = null;
        boolean inHunk = false;

        for (String line : lines) {
            // 新文件块开始
            if (line.startsWith("diff --git ")) {
                if (current != null) {
                    files.add(current);
                }
                current = new DiffFile();
                current.setPath(extractPath(line));
                current.setCategory(categorize(current.getPath()));
                inHunk = false;
            } else if (current == null) {
                continue;
            } else if (line.startsWith("new file mode")) {
                current.setChangeType("ADDED");
            } else if (line.startsWith("deleted file mode")) {
                current.setChangeType("DELETED");
            } else if (line.startsWith("rename from") || line.startsWith("rename to")) {
                current.setChangeType("RENAMED");
            } else if (line.startsWith("+++ ")) {
                // +++ b/path → 若 b/path 是 /dev/null 表示删除
                if (line.contains("/dev/null")) {
                    current.setChangeType(orDefault(current.getChangeType(), "DELETED"));
                }
                if (current.getChangeType() == null) {
                    current.setChangeType("MODIFIED");
                }
            } else if (line.startsWith("@@")) {
                inHunk = true;
                if (current.getChangeType() == null) {
                    current.setChangeType("MODIFIED");
                }
                current.getRawDiff().add(line);
            } else if (inHunk) {
                current.getRawDiff().add(line);
                if (line.startsWith("+") && !line.startsWith("+++")) {
                    current.getAddedLines().add(line.substring(1));
                } else if (line.startsWith("-") && !line.startsWith("---")) {
                    current.getDeletedLines().add(line.substring(1));
                }
            }
        }
        if (current != null) {
            files.add(current);
        }

        log.info("Diff 解析完成: {} 个文件, 变更行总计 {}",
                files.size(),
                files.stream().mapToInt(DiffFile::changedLineCount).sum());
        return files;
    }

    /** diff --git a/path b/path → 取 b 后的路径。 */
    private String extractPath(String line) {
        // "diff --git a/foo.java b/foo.java"
        int bIdx = line.indexOf(" b/");
        if (bIdx > 0) {
            return line.substring(bIdx + 3).trim();
        }
        // 兜底
        String[] parts = line.split("\\s+");
        return parts.length > 1 ? parts[parts.length - 1] : "unknown";
    }

    /** 按扩展名分类。 */
    private String categorize(String path) {
        if (path == null) return DiffFile.Category.OTHER.name();
        String lower = path.toLowerCase();
        if (lower.endsWith(".java")) return DiffFile.Category.JAVA.name();
        if (lower.endsWith(".sql")) return DiffFile.Category.SQL.name();
        if (lower.endsWith(".xml")) return DiffFile.Category.XML.name();
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")
                || lower.endsWith(".properties") || lower.endsWith(".json")) {
            return DiffFile.Category.CONFIG.name();
        }
        return DiffFile.Category.OTHER.name();
    }

    private String orDefault(String current, String def) {
        return current == null ? def : current;
    }
}
