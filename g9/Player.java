package pentos.g9;

import pentos.sim.Cell;
import pentos.sim.Building;
import pentos.sim.Land;
import pentos.sim.Move;

import java.util.*;

public class Player implements pentos.sim.Player {

    private Set<Cell> road_cells;
    private Random gen = new Random();

    public void init() {
        road_cells = new HashSet<Cell>();
    }

    public Move getMoveIfValid(Building request, Land land, int i, int j, int ri) {
        Cell p = new Cell(i, j);
        Building b = request.rotations()[ri];

        if (land.buildable(b, p)) {
            Move chosen = new Move(true, request, p, ri, new HashSet<Cell>(),
                                   new HashSet<Cell>(), new HashSet<Cell>());

            Set<Cell> shiftedCells = new HashSet<Cell>();
            for (Cell x : chosen.request.rotations()[chosen.rotation])
                shiftedCells.add(new Cell(x.i+chosen.location.i,x.j+chosen.location.j));

            Set<Cell> roadCells = findShortestRoad(shiftedCells, land);

            if (roadCells != null) {
                road_cells.addAll(roadCells);
                chosen.road = roadCells;

                if (request.type == Building.Type.RESIDENCE) {
                    Set<Cell> markedForConstruction = new HashSet<Cell>();
                    markedForConstruction.addAll(roadCells);
                    chosen.water = randomWalk(shiftedCells, markedForConstruction, land, 4);
                    markedForConstruction.addAll(chosen.water);
                    chosen.park = randomWalk(shiftedCells, markedForConstruction, land, 4);
                }

                return chosen;
            }
        }

        return null;
    }

    public Move play(Building request, Land land) {
        if (request.type == Building.Type.RESIDENCE) {
            for (int i = 0 ; i < land.side ; i++) {
                for (int j = 0 ; j < land.side ; j++) {
                    for (int ri = 0 ; ri < request.rotations().length ; ri++) {
                        Move chosen = getMoveIfValid(request, land, i, j, ri);
                        if (chosen != null) {
                            return chosen;
                        }
                    }
                }
            }
        } else if (request.type == Building.Type.FACTORY) {
            for (int i = land.side - 1 ; i >= 0; i--) {
                for (int j = land.side - 1 ; j >= 0; j--) {
                    for (int ri = 0 ; ri < request.rotations().length ; ri++) {
                        Move chosen = getMoveIfValid(request, land, i, j, ri);
                        if (chosen != null) {
                            return chosen;
                        }
                    }
                }
            }
        }

        return new Move(false);
    }
    
    private boolean isPerimeter(Land land, Cell cell) {
        int i = cell.i;
        int j = cell.j;

        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                if (di != 0 && dj != 0 && !land.unoccupied(i+di, j+dj)) {
                    return true;
                }
            }
        }

        return false;
    }


    private Set<Cell> findShortestRoad(Set<Cell> b, Land land) {
        Set<Cell> output = new HashSet<Cell>();
        boolean[][] checked = new boolean[land.side][land.side];
        Queue<Cell> queue = new LinkedList<Cell>();

        Cell source = new Cell(Integer.MAX_VALUE,Integer.MAX_VALUE);

        for (int z=0; z<land.side; z++) {
            if (b.contains(new Cell(0,z)) ||
                b.contains(new Cell(z,0)) ||
                b.contains(new Cell(land.side-1,z)) ||
                b.contains(new Cell(z,land.side-1))) {
                return output;
            }

            if (land.unoccupied(0,z)) {
                queue.add(new Cell(0,z,source));
            }

            if (land.unoccupied(z,0)) {
                queue.add(new Cell(z,0,source));
            }

            if (land.unoccupied(z,land.side-1)) {
                queue.add(new Cell(z,land.side-1,source));
            }

            if (land.unoccupied(land.side-1,z)) {
                queue.add(new Cell(land.side-1,z,source));
            }
        }

        // add cells adjacent to current road cells
        for (Cell p : road_cells) {
            for (Cell q : p.neighbors()) {
                if (!road_cells.contains(q) && land.unoccupied(q) && !b.contains(q) &&
                    isPerimeter(land, q)) {
                    queue.add(new Cell(q.i,q.j,p));
                }
            }
        }

        while (!queue.isEmpty()) {
            Cell p = queue.remove();
            checked[p.i][p.j] = true;

            for (Cell x : p.neighbors()) {
                if (b.contains(x)) { // trace back through search tree to find path
                    Cell tail = p;
                    while (!b.contains(tail) && !road_cells.contains(tail) &&
                           !tail.equals(source)) {
                        output.add(new Cell(tail.i,tail.j));
                        tail = tail.previous;
                    }

                    if (!output.isEmpty()) {
                        return output;
                    }
                } else if (!checked[x.i][x.j] && land.unoccupied(x.i,x.j) &&
                           isPerimeter(land, x)) {
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

    private Set<Cell> randomWalk(Set<Cell> b, Set<Cell> marked, Land land, int n) {
        ArrayList<Cell> adjCells = new ArrayList<Cell>();
        Set<Cell> output = new HashSet<Cell>();
        for (Cell p : b) {
            for (Cell q : p.neighbors()) {
                if (land.isField(q) || land.isPond(q)) {
                    return new HashSet<Cell>();
                }

                if (!b.contains(q) && !marked.contains(q) && land.unoccupied(q)) {
                    adjCells.add(q);
                }
            }
        }

        if (adjCells.isEmpty()) {
            return new HashSet<Cell>();
        }

        Cell tail = adjCells.get(gen.nextInt(adjCells.size()));
        for (int ii=0; ii<n; ii++) {
            ArrayList<Cell> walk_cells = new ArrayList<Cell>();
            for (Cell p : tail.neighbors()) {
                if (!b.contains(p) && !marked.contains(p) && land.unoccupied(p) &&
                    !output.contains(p)) {
                    walk_cells.add(p);
                }
            }

            if (walk_cells.isEmpty()) {
                return new HashSet<Cell>();
            }

            output.add(tail);	    
            tail = walk_cells.get(gen.nextInt(walk_cells.size()));
        }
        return output;
    }
}
