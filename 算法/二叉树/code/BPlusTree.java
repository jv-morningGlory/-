import java.util.ArrayList;
import java.util.List;

public class BPlusTree {

    static final int MAX_KEYS = 3;
    static final int MIN_KEYS = 1;

    private static class Node {
        boolean leaf;
        int n;
        int[] keys = new int[MAX_KEYS + 1];
        int[] vals = new int[MAX_KEYS + 1];
        Node[] children = new Node[MAX_KEYS + 2];
        Node next;
        Node parent;
    }

    private Node root;

    public BPlusTree() {
        root = new Node();
        root.leaf = true;
    }

    public Integer get(int key) {
        Node leaf = findLeaf(key);
        int i = indexOf(leaf, key);
        return i >= 0 ? leaf.vals[i] : null;
    }

    public boolean contains(int key) {
        return get(key) != null;
    }

    private Node findLeaf(int key) {
        Node cur = root;
        while (!cur.leaf) {
            int i = childIndex(cur, key);
            cur = cur.children[i];
        }
        return cur;
    }

    private int childIndex(Node internal, int key) {
        int i = 0;
        while (i < internal.n && key >= internal.keys[i]) {
            i++;
        }
        return i;
    }

    private int indexOf(Node leaf, int key) {
        for (int i = 0; i < leaf.n; i++) {
            if (leaf.keys[i] == key) {
                return i;
            }
        }
        return -1;
    }

    public void put(int key, int value) {
        Node leaf = findLeaf(key);
        int existing = indexOf(leaf, key);
        if (existing >= 0) {
            leaf.vals[existing] = value;
            return;
        }
        insertIntoLeaf(leaf, key, value);
        if (leaf.n > MAX_KEYS) {
            splitLeaf(leaf);
        }
    }

    private void insertIntoLeaf(Node leaf, int key, int value) {
        int i = leaf.n - 1;
        while (i >= 0 && leaf.keys[i] > key) {
            leaf.keys[i + 1] = leaf.keys[i];
            leaf.vals[i + 1] = leaf.vals[i];
            i--;
        }
        leaf.keys[i + 1] = key;
        leaf.vals[i + 1] = value;
        leaf.n++;
    }

    private void splitLeaf(Node leaf) {
        Node right = new Node();
        right.leaf = true;
        int mid = (leaf.n + 1) / 2;
        int rightN = leaf.n - mid;
        for (int i = 0; i < rightN; i++) {
            right.keys[i] = leaf.keys[mid + i];
            right.vals[i] = leaf.vals[mid + i];
        }
        right.n = rightN;
        leaf.n = mid;
        right.next = leaf.next;
        leaf.next = right;
        insertIntoParent(leaf, right.keys[0], right);
    }

    private void insertIntoParent(Node left, int key, Node right) {
        if (left.parent == null) {
            Node newRoot = new Node();
            newRoot.leaf = false;
            newRoot.keys[0] = key;
            newRoot.children[0] = left;
            newRoot.children[1] = right;
            newRoot.n = 1;
            left.parent = newRoot;
            right.parent = newRoot;
            root = newRoot;
            return;
        }
        Node parent = left.parent;
        int pos = 0;
        while (pos < parent.n && parent.children[pos] != left) {
            pos++;
        }
        for (int i = parent.n; i > pos; i--) {
            parent.keys[i] = parent.keys[i - 1];
            parent.children[i + 1] = parent.children[i];
        }
        parent.keys[pos] = key;
        parent.children[pos + 1] = right;
        right.parent = parent;
        parent.n++;
        if (parent.n > MAX_KEYS) {
            splitInternal(parent);
        }
    }

    private void splitInternal(Node node) {
        Node right = new Node();
        right.leaf = false;
        int mid = node.n / 2;
        int upKey = node.keys[mid];
        int rightN = node.n - mid - 1;
        for (int i = 0; i < rightN; i++) {
            right.keys[i] = node.keys[mid + 1 + i];
            right.children[i] = node.children[mid + 1 + i];
            right.children[i].parent = right;
        }
        right.children[rightN] = node.children[node.n];
        right.children[rightN].parent = right;
        right.n = rightN;
        node.n = mid;
        insertIntoParent(node, upKey, right);
    }

    public void remove(int key) {
        Node leaf = findLeaf(key);
        int idx = indexOf(leaf, key);
        if (idx < 0) {
            return;
        }
        for (int i = idx; i < leaf.n - 1; i++) {
            leaf.keys[i] = leaf.keys[i + 1];
            leaf.vals[i] = leaf.vals[i + 1];
        }
        leaf.n--;
        if (leaf == root) {
            return;
        }
        if (idx == 0) {
            updateSeparatorToLeafMin(leaf);
        }
        if (leaf.n < MIN_KEYS) {
            rebalanceLeaf(leaf);
        }
    }

    private void updateSeparatorToLeafMin(Node leaf) {
        if (leaf.n == 0) {
            return;
        }
        int min = leaf.keys[0];
        Node child = leaf;
        Node parent = leaf.parent;
        while (parent != null) {
            int pos = indexOfChild(parent, child);
            if (pos > 0) {
                parent.keys[pos - 1] = min;
                return;
            }
            child = parent;
            parent = parent.parent;
        }
    }

    private int indexOfChild(Node parent, Node child) {
        for (int i = 0; i <= parent.n; i++) {
            if (parent.children[i] == child) {
                return i;
            }
        }
        throw new IllegalStateException("child not found in parent");
    }

    private Node leftSibling(Node node) {
        if (node.parent == null) {
            return null;
        }
        int i = indexOfChild(node.parent, node);
        return i == 0 ? null : node.parent.children[i - 1];
    }

    private Node rightSibling(Node node) {
        if (node.parent == null) {
            return null;
        }
        int i = indexOfChild(node.parent, node);
        return i == node.parent.n ? null : node.parent.children[i + 1];
    }

    private void rebalanceLeaf(Node leaf) {
        Node left = leftSibling(leaf);
        Node right = rightSibling(leaf);
        if (left != null && left.n > MIN_KEYS) {
            for (int i = leaf.n; i > 0; i--) {
                leaf.keys[i] = leaf.keys[i - 1];
                leaf.vals[i] = leaf.vals[i - 1];
            }
            leaf.keys[0] = left.keys[left.n - 1];
            leaf.vals[0] = left.vals[left.n - 1];
            leaf.n++;
            left.n--;
            updateSeparatorToLeafMin(leaf);
            return;
        }
        if (right != null && right.n > MIN_KEYS) {
            leaf.keys[leaf.n] = right.keys[0];
            leaf.vals[leaf.n] = right.vals[0];
            leaf.n++;
            for (int i = 0; i < right.n - 1; i++) {
                right.keys[i] = right.keys[i + 1];
                right.vals[i] = right.vals[i + 1];
            }
            right.n--;
            updateSeparatorToLeafMin(right);
            return;
        }
        if (left != null) {
            mergeLeaves(left, leaf);
        } else if (right != null) {
            mergeLeaves(leaf, right);
        }
    }

    private void mergeLeaves(Node left, Node right) {
        for (int i = 0; i < right.n; i++) {
            left.keys[left.n] = right.keys[i];
            left.vals[left.n] = right.vals[i];
            left.n++;
        }
        left.next = right.next;
        removeChild(right.parent, right);
    }

    private void removeChild(Node parent, Node child) {
        int pos = indexOfChild(parent, child);
        int keyPos = Math.max(pos - 1, 0);
        if (pos == 0) {
            for (int i = 0; i < parent.n - 1; i++) {
                parent.keys[i] = parent.keys[i + 1];
            }
            for (int i = 0; i < parent.n; i++) {
                parent.children[i] = parent.children[i + 1];
            }
        } else {
            for (int i = keyPos; i < parent.n - 1; i++) {
                parent.keys[i] = parent.keys[i + 1];
            }
            for (int i = pos; i < parent.n; i++) {
                parent.children[i] = parent.children[i + 1];
            }
        }
        parent.children[parent.n] = null;
        parent.n--;
        if (parent == root) {
            if (parent.n == 0) {
                root = parent.children[0];
                root.parent = null;
            }
            return;
        }
        if (parent.n < MIN_KEYS) {
            rebalanceInternal(parent);
        }
    }

    private void rebalanceInternal(Node node) {
        Node left = leftSibling(node);
        Node right = rightSibling(node);
        if (left != null && left.n > MIN_KEYS) {
            int pos = indexOfChild(node.parent, node);
            for (int i = node.n; i > 0; i--) {
                node.keys[i] = node.keys[i - 1];
                node.children[i + 1] = node.children[i];
            }
            node.children[1] = node.children[0];
            node.keys[0] = node.parent.keys[pos - 1];
            node.children[0] = left.children[left.n];
            node.children[0].parent = node;
            node.n++;
            node.parent.keys[pos - 1] = left.keys[left.n - 1];
            left.children[left.n] = null;
            left.n--;
            return;
        }
        if (right != null && right.n > MIN_KEYS) {
            int pos = indexOfChild(node.parent, node);
            node.keys[node.n] = node.parent.keys[pos];
            node.children[node.n + 1] = right.children[0];
            node.children[node.n + 1].parent = node;
            node.n++;
            node.parent.keys[pos] = right.keys[0];
            for (int i = 0; i < right.n - 1; i++) {
                right.keys[i] = right.keys[i + 1];
                right.children[i] = right.children[i + 1];
            }
            right.children[right.n - 1] = right.children[right.n];
            right.children[right.n] = null;
            right.n--;
            return;
        }
        if (left != null) {
            mergeInternal(left, node);
        } else if (right != null) {
            mergeInternal(node, right);
        }
    }

    private void mergeInternal(Node left, Node right) {
        int pos = indexOfChild(left.parent, left);
        left.keys[left.n] = left.parent.keys[pos];
        left.n++;
        for (int i = 0; i < right.n; i++) {
            left.keys[left.n] = right.keys[i];
            left.children[left.n] = right.children[i];
            left.children[left.n].parent = left;
            left.n++;
        }
        left.children[left.n] = right.children[right.n];
        left.children[left.n].parent = left;
        removeChild(right.parent, right);
    }

    public List<Integer> range(int from, int to) {
        List<Integer> out = new ArrayList<>();
        Node leaf = findLeaf(from);
        while (leaf != null) {
            for (int i = 0; i < leaf.n; i++) {
                if (leaf.keys[i] < from) {
                    continue;
                }
                if (leaf.keys[i] > to) {
                    return out;
                }
                out.add(leaf.keys[i]);
            }
            leaf = leaf.next;
        }
        return out;
    }

    public List<Integer> allKeys() {
        List<Integer> out = new ArrayList<>();
        Node leaf = root;
        while (!leaf.leaf) {
            leaf = leaf.children[0];
        }
        while (leaf != null) {
            for (int i = 0; i < leaf.n; i++) {
                out.add(leaf.keys[i]);
            }
            leaf = leaf.next;
        }
        return out;
    }

    void validate() {
        List<Integer> keys = allKeys();
        for (int i = 1; i < keys.size(); i++) {
            if (keys.get(i - 1) >= keys.get(i)) {
                throw new AssertionError("leaf list not sorted: " + keys);
            }
        }
        validateNode(root, true);
    }

    private void validateNode(Node node, boolean isRoot) {
        if (!isRoot && node.n < MIN_KEYS) {
            throw new AssertionError("underflow n=" + node.n);
        }
        if (node.n > MAX_KEYS) {
            throw new AssertionError("overflow");
        }
        for (int i = 1; i < node.n; i++) {
            if (node.keys[i - 1] >= node.keys[i]) {
                throw new AssertionError("keys not sorted");
            }
        }
        if (node.leaf) {
            return;
        }
        if (node.children[0] == null || node.children[node.n] == null) {
            throw new AssertionError("missing child");
        }
        for (int i = 0; i <= node.n; i++) {
            Node child = node.children[i];
            if (child.parent != node) {
                throw new AssertionError("parent pointer broken");
            }
            validateNode(child, false);
            if (i > 0) {
                Node firstLeaf = child;
                while (!firstLeaf.leaf) {
                    firstLeaf = firstLeaf.children[0];
                }
                if (firstLeaf.n == 0 || firstLeaf.keys[0] != node.keys[i - 1]) {
                    throw new AssertionError("separator " + node.keys[i - 1]
                            + " != right subtree min " + (firstLeaf.n == 0 ? "empty" : firstLeaf.keys[0]));
                }
            }
        }
    }

    public static void main(String[] args) {
        BPlusTree t = new BPlusTree();
        for (int i = 1; i <= 20; i++) {
            t.put(i, i * 10);
            t.validate();
            if (t.get(i) == null || t.get(i) != i * 10) {
                throw new AssertionError("missing " + i);
            }
        }
        List<Integer> r = t.range(5, 12);
        if (!r.toString().equals("[5, 6, 7, 8, 9, 10, 11, 12]")) {
            throw new AssertionError("range: " + r);
        }
        int[] del = {1, 2, 3, 10, 20, 11, 12, 8, 15, 4, 19, 5};
        for (int v : del) {
            t.remove(v);
            t.validate();
            if (t.contains(v)) {
                throw new AssertionError("still contains " + v);
            }
        }
        for (int i = 1; i <= 20; i++) {
            boolean should = true;
            for (int d : del) {
                if (d == i) {
                    should = false;
                    break;
                }
            }
            if (t.contains(i) != should) {
                throw new AssertionError("contains " + i + " expected " + should);
            }
        }
        t.put(1, 1);
        t.put(2, 2);
        t.put(3, 3);
        t.validate();
        t.remove(6);
        t.remove(7);
        t.remove(9);
        t.remove(13);
        t.remove(14);
        t.remove(16);
        t.remove(17);
        t.remove(18);
        t.remove(1);
        t.remove(2);
        t.remove(3);
        t.validate();
        System.out.println("BPT_OK keys=" + t.allKeys());
    }
}
