package remote.claude.dto;

import java.util.List;

public class FileTreeResponse {
    private String path;
    private String name;
    private boolean directory;
    private long size;
    private List<FileTreeResponse> children;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isDirectory() { return directory; }
    public void setDirectory(boolean directory) { this.directory = directory; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public List<FileTreeResponse> getChildren() { return children; }
    public void setChildren(List<FileTreeResponse> children) { this.children = children; }
}