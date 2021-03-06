
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

class QuadTree {
    private Node root;
    private int width;
    private int height;
    
    public QuadTree(int width, int height) {
        this.width = width;
        this.height = height;
    }

    // helper node data type
    private class Node {
        int x, y;              // x- and y- coordinates
        Node NW, NE, SE, SW;   // four subtrees
        List<LabelGeneral> labels;           //labels having this point

        Node(int x, int y, LabelGeneral label) {
            this.x = x;
            this.y = y;
            this.labels = new ArrayList<>();
            labels.add(label);
        }
    }


  /***********************************************************************
    *  Insert (x, y) into appropriate quadrant
    ***********************************************************************/
    public void insert(LabelGeneral label) {
        root = insert(root, label.x, label.y, label);
        root = insert(root, label.x + width, label.y, label);
        root = insert(root, label.x, label.y + height, label);
        root = insert(root, label.x + width, label.y + height, label);
    }

    private Node insert(Node h, int x, int y, LabelGeneral label) {
        if (h == null) return new Node(x, y, label);
        if (x == h.x && y == h.y) h.labels.add(label);
        else if ( x < h.x &&  y < h.y) h.SW = insert(h.SW, x, y, label);
        else if ( x < h.x && y >= h.y) h.NW = insert(h.NW, x, y, label);
        else if (x >= h.x && y < h.y) h.SE = insert(h.SE, x, y, label);
        else if (x >= h.x && y >= h.y) h.NE = insert(h.NE, x, y, label);
        return h;
    }


  /***********************************************************************
    *  Range search.
    ***********************************************************************/

    public List<LabelGeneral> query2D(Interval2D<Integer> rect) {
        HashSet<LabelGeneral> result = new HashSet<>();
        query2D(root, rect, result);
        return new ArrayList<>(result);
    }

    private void query2D(Node h, Interval2D<Integer> rect, HashSet<LabelGeneral> result) {
        if (h == null) return;
        int xmin = rect.intervalX.low;
        int ymin = rect.intervalY.low;
        int xmax = rect.intervalX.high;
        int ymax = rect.intervalY.high;
        if (rect.contains(h.x, h.y)){
            for (LabelGeneral label : h.labels){
                if (label.x + width != xmin && label.y + height != ymin
                        && label.x != xmax && label.y != ymax ){
                        //&& !(label.x == xmin && label.y == ymin)){
                    //System.out.println(label.x+", "+xmax);
                    result.add(label);
                }
            }
        }
        if ( xmin < h.x &&  ymin < h.y) query2D(h.SW, rect, result);
        if ( xmin < h.x && ymax >= h.y) query2D(h.NW, rect, result);
        if (xmax >= h.x &&  ymin < h.y) query2D(h.SE, rect, result);
        if (xmax >= h.x && ymax >= h.y) query2D(h.NE, rect, result);
    }
    
}
