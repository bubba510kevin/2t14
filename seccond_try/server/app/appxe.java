import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
public class appxe {
    private JFrame frame;
    private DefaultMutableTreeNode rootNode;
    private JTree tree;
    private JTextArea previewArea;
    private JButton downloadBtn;
    
    private static class FileNode {
        String name;
        String type; // file or dir
        String path; // relative path from server base
        FileNode(String name, String type, String path) {
            this.name = name;
            this.type = type;
            this.path = path;
        }
        public String toString() { return name; }
    }

    private void buildGUI() {
        frame = new JFrame("Server File Browser");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);

        rootNode = new DefaultMutableTreeNode("root");
        tree = new JTree(rootNode);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        JScrollPane treeScroll = new JScrollPane(tree);
        previewArea = new JTextArea();
        previewArea.setEditable(false);
        JScrollPane previewScroll = new JScrollPane(previewArea);

        downloadBtn = new JButton("Download");
        downloadBtn.addActionListener(e -> downloadSelectedFile());

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, previewScroll);
        splitPane.setDividerLocation(300);

        frame.add(splitPane, BorderLayout.CENTER);
        frame.add(downloadBtn, BorderLayout.SOUTH);

        // Expand folders on double-click
        tree.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath tp = tree.getSelectionPath();
                    if (tp != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tp.getLastPathComponent();
                        Object userObj = node.getUserObject();
                        if (userObj instanceof FileNode) {
                            FileNode fn = (FileNode) userObj;
                            if (fn.type.equals("dir") && node.getChildCount() == 0) {
                                loadDirectory(fn.path, node);
                            } else if (fn.type.equals("file")) {
                                previewFile(fn.path);
                            }
                        }
                    }
                }
            }
        });

        frame.setVisible(true);
    }

    private void loadDirectory(String relativePath) {
        loadDirectory(relativePath, rootNode);
    }

    private void loadDirectory(String relativePath, DefaultMutableTreeNode parentNode) {
        try {
            out.println("list " + relativePath);
            String response = in.readLine();

            if (response.contains("\"error\":\"NOTFOUND\"")) return;

            // Simple parsing without JSON library
            String filesArray = response.substring(response.indexOf("[")+1, response.lastIndexOf("]"));
            String[] entries = filesArray.split("\\},\\{");
            for (String entry : entries) {
                entry = entry.replace("{","").replace("}","");
                String[] kvs = entry.split(",");
                String name="", type="";
                for (String kv : kvs) {
                    String[] pair = kv.split(":");
                    String key = pair[0].replace("\"","").trim();
                    String val = pair[1].replace("\"","").trim();
                    if (key.equals("name")) name = val;
                    if (key.equals("type")) type = val;
                }
                FileNode fn = new FileNode(name, type, relativePath.isEmpty() ? name : relativePath + "/" + name);
                DefaultMutableTreeNode child = new DefaultMutableTreeNode(fn);
                parentNode.add(child);
            }

            ((DefaultTreeModel) tree.getModel()).reload(parentNode);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void previewFile(String path) {
        try {
            out.println("preview " + path);
            String status = in.readLine();
            if (!"OK".equals(status)) {
                previewArea.setText("File not found or cannot preview.");
                return;
            }

            DataInputStream dataIn = new DataInputStream(socket.getInputStream());
            long size = dataIn.readLong();
            byte[] buf = new byte[(int)size];
            dataIn.readFully(buf);
            String previewText = new String(buf); // assuming text files
            previewArea.setText(previewText);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void downloadSelectedFile() {
        TreePath tp = tree.getSelectionPath();
        if (tp == null) return;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tp.getLastPathComponent();
        if (!(node.getUserObject() instanceof FileNode)) return;

        FileNode fn = (FileNode) node.getUserObject();
        if (!fn.type.equals("file")) return;

        try {
            out.println("get " + fn.path);
            String status = in.readLine();
            if (!"OK".equals(status)) {
                JOptionPane.showMessageDialog(frame, "File not found on server.");
                return;
            }

            DataInputStream dataIn = new DataInputStream(socket.getInputStream());
            long size = dataIn.readLong();

            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File(fn.name));
            int choice = chooser.showSaveDialog(frame);
            if (choice != JFileChooser.APPROVE_OPTION) return;

            File outFile = chooser.getSelectedFile();
            try (OutputStream fos = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[8192];
                long remaining = size;
                int read;
                while (remaining > 0 && (read = dataIn.read(buffer,0,(int)Math.min(buffer.length,remaining))) != -1) {
                    fos.write(buffer,0,read);
                    remaining -= read;
                }
            }
            JOptionPane.showMessageDialog(frame, "Download complete: " + outFile.getAbsolutePath());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
