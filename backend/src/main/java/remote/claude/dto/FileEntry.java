package remote.claude.dto;

import java.util.List;

public class FileEntry {
    private String name;
    private String path;
    private boolean directory;
    private long size;
    private String mimeType;
    private List<FileEntry> children;

    public FileEntry() {}

    public FileEntry(String name, String path, boolean directory) {
        this.name = name;
        this.path = path;
        this.directory = directory;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public boolean isDirectory() { return directory; }
    public void setDirectory(boolean directory) { this.directory = directory; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public List<FileEntry> getChildren() { return children; }
    public void setChildren(List<FileEntry> children) { this.children = children; }
}