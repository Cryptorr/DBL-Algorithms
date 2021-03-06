
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;


public class ForceDirectedSimulatedAnnealing extends LabelSolver {
    final int MAX_ITERATIONS = Integer.MAX_VALUE;
    
    final double MIN_FORCE = 0.5;
    
    final double DEFAULT_FORCE_FAKT_REPULSIVE = 1.0;
    final double DEFAULT_FORCE_FAKT_OVERLAPPING = 10.0;
    final double DEFAULT_FORCE_FAKT_EPS = 0.5;
    final double DEFAULT_OVERLAPPING_PENALTY = 3 * (1 / (DEFAULT_FORCE_FAKT_EPS * DEFAULT_FORCE_FAKT_EPS));

    double temperature = 0;
    double cooling_rate = 0;
    int    moves_per_stage = 0;

    int   nRejected = 0;
    int   nTaken = 0;
    int   nUnsignificant = 0;
    int   nStages = 0;
    int   nIterations = 0;

    HashSet<ForceLabel> obstructed = null;

    double overallForce = 0.0;
    
    List<PointData> pointList;
    
    SliderQuadTree QT;
     
    public ForceDirectedSimulatedAnnealing(int width, int height) {
        pointList = new ArrayList<>();
        QT = new SliderQuadTree();
    }

    @Override
    List<PointData> getLabeledPoints1slider(List<Point> points) {
        if (Globals.numberOfPoints > 100)
            return FDSA10000(points);
        else
            return FDSA100(points);
            
    }
    
    List<PointData> FDSA100(List<Point> points) {
        
        ArrayList<ForceLabel> labelList = new ArrayList<ForceLabel>();
        obstructed = new HashSet<ForceLabel>();
        //Place labels on random position
        for (Point point : points) {
            double shift = Math.random();
            //System.out.println(shift);
            int xPos = (int)(shift * (double)Globals.width);
            ForceLabel label = new ForceLabel(point.x - Globals.width + xPos, point.y);
            ForcePointData pointData = new ForcePointData(point.x, point.y, label);
            label.point = pointData;
            pointList.add(pointData);
            labelList.add(label);
            QT.insert(label);
        }
        //Find neighbour
        for (ForceLabel label : labelList){
            List<ForceLabel> neighbours = QT.findNeighbours(label);
            for(ForceLabel otherLabel : neighbours){
                double force = 0.0;
                if((label.x + Globals.width) > otherLabel.x && (otherLabel.x + Globals.width) > label.x){ //Are the labels overlapping?
                    if(label.x > otherLabel.x){ //other label is left from this label
                        force = DEFAULT_FORCE_FAKT_OVERLAPPING * ((otherLabel.x + Globals.width) - label.x) + DEFAULT_OVERLAPPING_PENALTY;
                    } else if(label.x < otherLabel.x){ //other label is right from this label
                        force = -DEFAULT_FORCE_FAKT_OVERLAPPING * ((label.x + Globals.width) - otherLabel.x) - DEFAULT_OVERLAPPING_PENALTY;
                    } else { //Labels are on the same position
                        force = -otherLabel.neighbours.getOrDefault(label, DEFAULT_FORCE_FAKT_OVERLAPPING * ((otherLabel.x + Globals.width) - label.x) + DEFAULT_OVERLAPPING_PENALTY);
                    }
                }
                label.totalForce += force;
                label.neighbours.put(otherLabel, force);
                otherLabel.neighbours.put(label, -force);
            }
            overallForce += Math.abs(label.totalForce);
        }
        
        //init temperature && initialize set of obstructed labels...
        double avg_lbl_size = Globals.height * Globals.width;
        for (ForceLabel label : labelList){
            if(!label.unplacable && (label.isOverlapping() || canSlide(label)))
                obstructed.add(label);
        }
        //we accept a overlap of p2 of the average label size with p1
        double p1 = 0.3; //propability of acceptance
        double p2 = 0.5; //percentage of overlap
        double eps_2 = DEFAULT_FORCE_FAKT_EPS * DEFAULT_FORCE_FAKT_EPS;
        temperature = avg_lbl_size * p2 * DEFAULT_FORCE_FAKT_OVERLAPPING + DEFAULT_OVERLAPPING_PENALTY + DEFAULT_FORCE_FAKT_REPULSIVE / eps_2;
        temperature /= -Math.log(p1);

        //temp. should become < 1 after N stages...	
        double N = 15;	
        cooling_rate = Math.pow(1. / temperature, 1. / N);

        //moves per stage...
        if (Globals.numberOfPoints == 10000) {
            moves_per_stage = 1;
        } 
        else {
            moves_per_stage = 30 * Globals.numberOfPoints;
        }
        
        while (!obstructed.isEmpty() && nIterations < MAX_ITERATIONS) {
            nIterations ++;
            
            ForceLabel current = chooseNextCandidate();
            
            double old_force = overallForce;
            int old_position = current.x;
            
            if (canSlide(current)){
                findEquilibrium(current);
                if(canSlide(current)){
                    randomPlace(current);
                }
            } else {
                randomPlace(current);
            }
            
            double dE = overallForce - old_force;
            double p = Math.random();

            if (dE > 0.0 && p > Math.exp(-dE / temperature)){
                //reject move
                current.x = old_position;
                updateForces(current);
                nRejected ++;
            }
            else {
                //update set of obstructed labels....
                if(!current.isOverlapping() && !canSlide(current))
                    obstructed.remove(current);

                Iterator<ForceLabel> ni = current.neighbours.keySet().iterator();
                while (ni.hasNext()){
                    ForceLabel ln = ni.next();
                    if(ln.isOverlapping() || canSlide(ln)){
                        obstructed.add(ln);
                    }else
                        obstructed.remove(ln);
                }

                nTaken ++;
                if(Math.abs(dE) < MIN_FORCE)
                        nUnsignificant ++;

            }
            
            if (nTaken + nRejected >= moves_per_stage){
                int max_ovl = 0;
                ForceLabel candidate = null;

                Iterator<ForceLabel> d = obstructed.iterator();
                while(d.hasNext()){
                    ForceLabel label = d.next();
                    int n = 0;

                    Iterator<ForceLabel> it = label.neighbours.keySet().iterator();
                    while(it.hasNext()){
                        ForceLabel otherLabel = it.next();
                        if(!otherLabel.unplacable && ((label.x + Globals.width) > otherLabel.x && (otherLabel.x + Globals.width) > label.x))
                            n ++;
                    }

                    if(n > max_ovl){
                        max_ovl = n;
                        candidate = label;
                    }
                }

                if(candidate == null){
                    //We are done
                    break;
                }

                if(nTaken - nUnsignificant <= 0){
                    //Remove candidate label
                    removeLabel(candidate);
                }

                //decrease temperature
                temperature = temperature * cooling_rate;
                
                //adjust moves_per_stage
                moves_per_stage = Math.max(Globals.numberOfPoints, Math.min(50 * obstructed.size(), 10 * Globals.numberOfPoints));

                nStages++;

                nRejected = 0;
                nTaken = 0;
                nUnsignificant = 0;
            }
        }
        
        return pointList;
    }
    
    List<PointData> FDSA10000(List<Point> points) {
        
        ArrayList<ForceLabel> labelList = new ArrayList<ForceLabel>();
        obstructed = new HashSet<ForceLabel>();
        //Place labels on random position
        for (Point point : points) {
            double shift = Math.random();
            //System.out.println(shift);
            int xPos = (int)(shift * (double)Globals.width);
            ForceLabel label = new ForceLabel(point.x - Globals.width + xPos, point.y);
            ForcePointData pointData = new ForcePointData(point.x, point.y, label);
            label.point = pointData;
            pointList.add(pointData);
            labelList.add(label);
            QT.insert(label);
        }
        //Find neighbour
        for (ForceLabel label : labelList){
            List<ForceLabel> neighbours = QT.findNeighbours(label);
            for(ForceLabel otherLabel : neighbours){
                double force = 0.0;
                if((label.x + Globals.width) > otherLabel.x && (otherLabel.x + Globals.width) > label.x){ //Are the labels overlapping?
                    if(label.x > otherLabel.x){ //other label is left from this label
                        force = DEFAULT_FORCE_FAKT_OVERLAPPING * ((otherLabel.x + Globals.width) - label.x) + DEFAULT_OVERLAPPING_PENALTY;
                    } else if(label.x < otherLabel.x){ //other label is right from this label
                        force = -DEFAULT_FORCE_FAKT_OVERLAPPING * ((label.x + Globals.width) - otherLabel.x) - DEFAULT_OVERLAPPING_PENALTY;
                    } else { //Labels are on the same position
                        force = -otherLabel.neighbours.getOrDefault(label, DEFAULT_FORCE_FAKT_OVERLAPPING * ((otherLabel.x + Globals.width) - label.x) + DEFAULT_OVERLAPPING_PENALTY);
                    }
                }
                label.totalForce += force;
                label.neighbours.put(otherLabel, force);
                otherLabel.neighbours.put(label, -force);
            }
            overallForce += Math.abs(label.totalForce);
        }
        
        //init temperature && initialize set of obstructed labels...
        double avg_lbl_size = Globals.height * Globals.width;
        for (ForceLabel label : labelList){
            if(!label.unplacable && (label.isOverlapping() || canSlide(label)))
                obstructed.add(label);
        }
        //we accept a overlap of p2 of the average label size with p1
        double p1 = 0.3; //propability of acceptance
        double p2 = obstructed.size()/Globals.numberOfPoints; //percentage of overlap
        double eps_2 = DEFAULT_FORCE_FAKT_EPS * DEFAULT_FORCE_FAKT_EPS;
        temperature = avg_lbl_size * p2 * DEFAULT_FORCE_FAKT_OVERLAPPING + DEFAULT_OVERLAPPING_PENALTY + DEFAULT_FORCE_FAKT_REPULSIVE / eps_2;
        temperature /= -Math.log(p1);

        //temp. should become < 1 after N stages...	
        double N = 15;	
        cooling_rate = Math.pow(1. / temperature, 1. / N);

        //moves per stage...
        moves_per_stage = 30 * Globals.numberOfPoints;
        
        //long startTime = System.nanoTime();
        //long oldTime = startTime;
        while (!obstructed.isEmpty()) {
            //nIterations ++;
            ForceLabel current = chooseNextCandidate();
            
            double old_force = overallForce;
            int old_position = current.x;
            
            if (canSlide(current)){
                findEquilibrium(current);
                if(canSlide(current)){
                    randomPlace(current);
                }
            } else {
                randomPlace(current);
            }
            
            double dE = overallForce - old_force;
            double p = Math.random();

            if (dE > 0.0 && p > Math.exp(-dE / temperature)){
                //reject move
                current.x = old_position;
                updateForces(current);
                nRejected ++;
            }
            else {
                //update set of obstructed labels....
                if(current.isOverlapping() && !canSlide(current))
                    obstructed.remove(current);

                Iterator<ForceLabel> ni = current.neighbours.keySet().iterator();
                while (ni.hasNext()){
                    ForceLabel ln = ni.next();
                    if(ln.isOverlapping() || canSlide(ln)){
                        obstructed.add(ln);
                    }else
                        obstructed.remove(ln);
                }

                nTaken ++;
                if(Math.abs(dE) < MIN_FORCE)
                        nUnsignificant ++;

            }
            
            if (nTaken + nRejected >= moves_per_stage){
                //System.out.println("Iteration: " + nIterations + " temperature: " + temperature + " force: " + overallForce + " obstructed: " + obstructed.size());
                int max_ovl = 0;
                ForceLabel candidate = null;

                Iterator<ForceLabel> d = obstructed.iterator();
                while(d.hasNext()){
                    ForceLabel label = d.next();
                    int n = 0;
                    Iterator<ForceLabel> it = label.neighbours.keySet().iterator();
                    while(it.hasNext()){
                        ForceLabel otherLabel = it.next();
                        if(!otherLabel.unplacable && ((label.x + Globals.width) > otherLabel.x && (otherLabel.x + Globals.width) > label.x))
                            n++;

                    }

                    if(n > max_ovl){
                        max_ovl = n;
                        candidate = label;
                    }
                }

                if(candidate == null){
                    //We are done
                    break;
                }
                

                
                if(nTaken - nUnsignificant <= 0){
                    //Remove candidate label
                    removeLabel(candidate);
                }

                //decrease temperature
                temperature = temperature * cooling_rate;
                
                //long newTime = System.nanoTime();
                //long timeDifference = oldTime - newTime;
                
                //adjust moves_per_stage
                moves_per_stage = Math.max(Globals.numberOfPoints, Math.min(50 * obstructed.size(), 10 * Globals.numberOfPoints));
                
                nStages++;

                nRejected = 0;
                nTaken = 0;
                nUnsignificant = 0;
                
                //oldTime = newTime;
            }
        }
       
        return pointList;
    }
    
    boolean canSlide(ForceLabel label){
        return (
            Math.abs(label.totalForce) >= MIN_FORCE
                    && ((label.totalForce > 0.0 && label.point.getShift() > 0)
                    || (label.totalForce < 0.0 && label.point.getShift() < 1)));

    }
    
    
    
    void randomPlace(ForceLabel label) {
        double shift = Math.random();
        label.x = label.point.x - Globals.width + (int)(shift * (double)Globals.width);
        updateForces(label);
    }
    
    ForceLabel chooseNextCandidate(){
        if(!obstructed.isEmpty()) {
            Random random = new Random();
            
            int i = random.nextInt(obstructed.size());
            Iterator it = obstructed.iterator();
            ForceLabel l = null;
            for(int j=0; j<= i && it.hasNext(); j++)
                l = (ForceLabel)it.next();

            return l;
        }
	return null;
    }
    
    void removeLabel(ForceLabel label) {
        label.unplacable = true;
        label.point.label = null;
        obstructed.remove(label);
        
        overallForce -= Math.abs(label.totalForce);
        label.totalForce = 0.0;
        
        for(ForceLabel otherLabel : label.neighbours.keySet()){
            overallForce -= Math.abs(otherLabel.totalForce);
            otherLabel.totalForce -= otherLabel.neighbours.get(label);
            otherLabel.neighbours.put(label, 0.0);
            label.neighbours.put(otherLabel, 0.0);
            overallForce += Math.abs(otherLabel.totalForce);
            //is the neighour still obstructed?
            if(!otherLabel.unplacable && (otherLabel.isOverlapping() || canSlide(otherLabel)))
                obstructed.add(otherLabel);
            else
                obstructed.remove(otherLabel);
        }
        
    }
    
    void findEquilibrium (ForceLabel label) {
        if (Math.abs(label.totalForce) < MIN_FORCE) {
            //System.out.println("Label already in equilibrium");
            return;
        }
        
        int total = 0; //Amount we can move
        int amount = 0; //Amount we move
        int old_direction = 0;
        int iteration = 0;
        
        //Label wants to move right
        if (label.totalForce > 0){
            total = label.point.x - label.x;
            old_direction = 1;
        } else {//Label wants to move left
            total = (label.x + Globals.width) - label.point.x;
            old_direction = -1;
        }
        
        amount = (int)Math.ceil((double)total * 0.2);
        
        while (iteration < 20 && Math.abs(label.totalForce) >= MIN_FORCE && amount > 0){
            label.x += old_direction * amount;
            
            //Ensure it can't be placed outside its point range
            if(label.x < label.point.x - Globals.width){
                label.x = label.point.x - Globals.width;
            } else if(label.x > label.point.x) {
                label.x = label.point.x;
            }
            updateForces(label);
            
            int new_direction = 0;
            //Label wants to move right
            if (label.totalForce > 0){
                new_direction = 1;
            } else {//Label wants to move left
                new_direction = -1;
            }
                    
            if(old_direction != new_direction){
                old_direction = new_direction;
                amount /= 2.;
            }
            
            iteration ++;
        }
    }
    
    void updateForces(ForceLabel label) {
        overallForce -= Math.abs(label.totalForce);
        label.totalForce = 0.0;
        
        for(ForceLabel otherLabel : label.neighbours.keySet()) {
            if (!otherLabel.unplacable){
                overallForce -= Math.abs(otherLabel.totalForce);
                otherLabel.totalForce -= otherLabel.neighbours.get(label);
                double force = 0.0;
                if((label.x + Globals.width) > otherLabel.x && (otherLabel.x + Globals.width) > label.x){ //Are the labels overlapping?
                    if(label.x > otherLabel.x){ //other label is left from this label
                        force = DEFAULT_FORCE_FAKT_OVERLAPPING * ((otherLabel.x + Globals.width) - label.x) + DEFAULT_OVERLAPPING_PENALTY;
                    } else if(label.x < otherLabel.x){ //other label is right from this label
                        force = -DEFAULT_FORCE_FAKT_OVERLAPPING * ((label.x + Globals.width) - otherLabel.x) - DEFAULT_OVERLAPPING_PENALTY;
                    } else { //Labels are on the same position
                        force = -otherLabel.neighbours.getOrDefault(label, DEFAULT_FORCE_FAKT_OVERLAPPING * ((otherLabel.x + Globals.width) - label.x) + DEFAULT_OVERLAPPING_PENALTY);
                    }
                }
                
                label.neighbours.put(otherLabel, force);
                otherLabel.neighbours.put(label, -force);
                label.totalForce += force;
                otherLabel.totalForce += -force;
                overallForce += Math.abs(otherLabel.totalForce);
            }
        }
        
        overallForce += Math.abs(label.totalForce);
    }
    
    @Override
    List<PointData> getLabeledPoints2pos(List<Point> points) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    List<PointData> getLabeledPoints4pos(List<Point> points) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
