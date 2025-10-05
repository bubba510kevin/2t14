import java.awt.*;
import java.io.*;
import java.net.*;
import javax.swing.*;
import javax.swing.tree.*;

public class app extends JFrame {

    private JTree fileTree;
    private JTextArea previewArea;
    private JButton downloadButton;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    // 👇 Constructor
    public app(String host, int port) {
        setTitle("File Client GUI");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Connect to server
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not connect to server: " + e.getMessage());
            System.exit(1);
        }

        // File tree panel
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Server Files");
        fileTree = new JTree(root);
        JScrollPane treeScroll = new JScrollPane(fileTree);

        // Preview panel
        previewArea = new JTextArea();
        previewArea.setEditable(false);
        JScrollPane previewScroll = new JScrollPane(previewArea);

        // Download button
        downloadButton = new JButton("Download");
        downloadButton.addActionListener(e -> downloadFile());

        // Layout
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, previewScroll);
        splitPane.setDividerLocation(300);

        add(splitPane, BorderLayout.CENTER);
        add(downloadButton, BorderLayout.SOUTH);

        // Load server file structure
        loadFileTree(root);

        // Add file click listener
        fileTree.addTreeSelectionListener(event -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
            if (node == null || node.isRoot()) return;

            String filePath = node.getUserObject().toString();
            previewFile(filePath);
        });
    }

    // 👇 Load file tree from server
    private void loadFileTree(DefaultMutableTreeNode root) {
        try {
            out.println("LIST");
            String line;
            while (!(line = in.readLine()).equals("END")) {
                root.add(new DefaultMutableTreeNode(line));
            }
            ((DefaultTreeModel) fileTree.getModel()).reload();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 👇 Preview a file
    private void previewFile(String filePath) {
        try {
            out.println("PREVIEW " + filePath);
            previewArea.setText("");
            String line;
            while (!(line = in.readLine()).equals("END")) {
                previewArea.append(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 👇 Download a file
    private void downloadFile() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
        if (node == null || node.isRoot()) return;

        String filePath = node.getUserObject().toString();
        try {
            out.println("DOWNLOAD " + filePath);

            FileOutputStream fos = new FileOutputStream(new File(filePath));
            InputStream is = socket.getInputStream();

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                if (bytesRead < 4096) break;
            }
            fos.close();

            JOptionPane.showMessageDialog(this, "Downloaded: " + filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 👇 Method to launch GUI (instead of main)
    public void start() {
        SwingUtilities.invokeLater(() -> setVisible(true));
    }
}


