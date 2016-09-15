package pentos.g9;

import pentos.sim.Cell;
import pentos.sim.Building;
import pentos.sim.Land;
import pentos.sim.Move;

import java.util.*;

public class Player implements pentos.sim.Player {

    private Set<Cell> road_cells = new HashSet<Cell>();

    public void init() {
    }
    
    public Move play(Building request, Land land) {
        ArrayList <Move> moves = new ArrayList <Move> ();

        if (request.type == Building.Type.RESIDENCE) {
            for (int i = 0 ; i < land.side ; i++) {
                for (int j = 0 ; j < land.side ; j++) {
                    Cell p = new Cell(i, j);
                    Building[] rotations = request.rotations();
                    for (int ri = 0 ; ri < rotations.length ; ri++) {
                        Building b = rotations[ri];
                        if (land.buildable(b, p)) {
                            moves.add(new Move(true, request, p, ri, new HashSet<Cell>(), new HashSet<Cell>(), new HashSet<Cell>()));
                        }
                    }
                }
            }
        } else {
            for (int i = land.side - 1 ; i >= 0; i--) {
                for (int j = land.side - 1 ; j >= 0; j--) {
                    Cell p = new Cell(i, j);
                    Building[] rotations = request.rotations();
                    for (int ri = 0 ; ri < rotations.length ; ri++) {
                        Building b = rotations[ri];
                        if (land.buildable(b, p)) {
                            moves.add(new Move(true, request, p, ri, new HashSet<Cell>(), new HashSet<Cell>(), new HashSet<Cell>()));
                        }
                    }
                }
            }
        }

        if (moves.isEmpty()) {
            System.out.print("COULD NOT PLACE BUILDING " + request.toString() + "\n");
            return new Move(false);
        }

        for (Move chosen : moves) {
            Set<Cell> shiftedCells = new HashSet<Cell>();
            for (Cell x : chosen.request.rotations()[chosen.rotation])
                shiftedCells.add(new Cell(x.i+chosen.location.i,x.j+chosen.location.j));

            Set<Cell> roadCells = findShortestRoad(shiftedCells, land);

            if (roadCells != null) {
                road_cells.addAll(roadCells);
                chosen.road = roadCells;
                return chosen;
            } else {
                System.out.print("COULD NOT BUILD ROAD" + request.toString() + "\n");
                continue;
            }
        }

        return new Move(false);
    }


    private Set<Cell> findShortestRoad(Set<Cell> b, Land land) {
        Set<Cell> output = new HashSet<Cell>();
        boolean[][] checked = new boolean[land.side][land.side];
        Queue<Cell> queue = new LinkedList<Cell>();
        // add border cells that don't have a road currently
        Cell source = new Cell(Integer.MAX_VALUE,Integer.MAX_VALUE); // dummy cell to serve as road connector to perimeter cells
        for (int z=0; z<land.side; z++) {
            if (b.contains(new Cell(0,z)) || b.contains(new Cell(z,0)) || b.contains(new Cell(land.side-1,z)) || b.contains(new Cell(z,land.side-1))) //if already on border don't build any roads
            return output;
            if (land.unoccupied(0,z))
            queue.add(new Cell(0,z,source));
            if (land.unoccupied(z,0))
            queue.add(new Cell(z,0,source));
            if (land.unoccupied(z,land.side-1))
            queue.add(new Cell(z,land.side-1,source));
            if (land.unoccupied(land.side-1,z))
            queue.add(new Cell(land.side-1,z,source));
        }
        // add cells adjacent to current road cells
        for (Cell p : road_cells) {
            for (Cell q : p.neighbors()) {
            if (!road_cells.contains(q) && land.unoccupied(q) && !b.contains(q)) 
                queue.add(new Cell(q.i,q.j,p)); // use tail field of cell to keep track of previous road cell during the search
            }
        }	
        while (!queue.isEmpty()) {
            Cell p = queue.remove();
            checked[p.i][p.j] = true;
            for (Cell x : p.neighbors()) {		
            if (b.contains(x)) { // trace back through search tree to find path
                Cell tail = p;
                while (!b.contains(tail) && !road_cells.contains(tail) && !tail.equals(source)) {
                output.add(new Cell(tail.i,tail.j));
                tail = tail.previous;
                }
                if (!output.isEmpty())
                return output;
            }
            else if (!checked[x.i][x.j] && land.unoccupied(x.i,x.j)) {
                x.previous = p;
                queue.add(x);	      
            } 

            }
        }
        if (output.isEmpty() && queue.isEmpty())
            return null;
        else
            return output;
    }
}
