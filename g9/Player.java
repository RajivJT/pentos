package pentos.g9;

import pentos.sim.Cell;
import pentos.sim.Building;
import pentos.sim.Land;
import pentos.sim.Move;

import java.util.*;

public class Player implements pentos.sim.Player {

    private int BASE_RESIDENCE_SCORE = 50; // base score for a residence
    private int PACKING_FACTOR_MULTIPLE = 10; // score multiple for each adjacent cell
    private int POND_BONUS_SCORE = 20; // score to add for a pond
    private int FIELD_BONUS_SCORE = 20; // score to add for a field
    
    private Set<Cell> road_cells;
    private Random gen = new Random();
    private int resAreaCount = 1;

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


    /*
      Returns number of adjacent empty cells (how well packed the building is)
     */
    public int getPackingFactor(Building b, Cell position, Land land) {
        Set<Cell> emptyNeighbors = new HashSet<Cell>();
        Set<Cell> absBuildingCells = new HashSet<Cell>();
        
        for (Cell c : b) {
            Cell abs = new Cell(c.i + position.i, c.j + position.j);
            absBuildingCells.add(abs);
        }

        for (Cell abs : absBuildingCells) {
            Cell[] absNeighbors = abs.neighbors();
            for (Cell n : absNeighbors) {
                if (land.unoccupied(n)) {
                    // check if that cell on the land WOULD be occupied by this building
                    boolean occupied = false;
                    for (Cell d : absBuildingCells) {
                        if (d.equals(abs))
                            continue; // neighbor is next to the cell we used to get this neighbor, ignore!
                        if (n.equals(d)) {
                            occupied = true;
                        }
                    }
                    if (occupied == false) {
                        emptyNeighbors.add(n);
                    }
                }
            }
        }
        return emptyNeighbors.size() * PACKING_FACTOR_MULTIPLE;
    }

    public boolean adjacentPond(Building b, Cell position, Land land) {
        Set<Cell> adjacentPoints = new HashSet<Cell>();
        for (Cell c : b) {
            Cell abs = new Cell(c.i + position.i, c.j + position.j);
            Cell[] adj = abs.neighbors();
            for (Cell a : adj) {
                adjacentPoints.add(a);
            }
        }

        for (Cell p : adjacentPoints) {
            if (land.isPond(p)) {
                return true;
            }
        }
        return false;
    }

    public boolean adjacentField(Building b, Cell position, Land land) {
        Set<Cell> adjacentPoints = new HashSet<Cell>();
        for (Cell c : b) {
            Cell abs = new Cell(c.i + position.i, c.j + position.j);
            Cell[] adj = abs.neighbors();
            for (Cell a : adj) {
                adjacentPoints.add(a);
            }
        }

        for (Cell p : adjacentPoints) {
            if (land.isField(p)) {
                return true;
            }
        }
        return false;
    }

    /*
      Checks if cell is occupied currently or will be by the building about to be placed
      c is an ABSOLUTE POSITION on the land
     */
    public boolean willBeUnoccupied(Cell c, Building b, Cell buildingPos, Land land) {
        if (!land.unoccupied(c)) {
            return false; // it IS occupied
        }

        for (Cell buildingCell : b) {
            Cell buildingCellAbs = new Cell(buildingCell.i + buildingPos.i, buildingCell.j + buildingPos.j);
            if (buildingCellAbs.equals(c)) {
                return false; // it WILL BE occupied by the building about to be placed
            }
        }

        return true; // will be unoccupied
    }

    public Double getDist(Cell a, Cell b) {
        return Math.hypot(a.i - b.i, a.j - b.j);
    }

    /* returns a pond that is as packed as possible to the building about to be built
       if not possible, returns empty set
       TODO: use recursive search to generate ponds. right now only builds to the right
     */
    public Set<Cell> buildPackedPondField(Building b, Cell buildingPos, Land land) {
        // calculate building's center of mass **UNUSED
        int sumI = 0;
        int sumJ = 0;
        for (Cell c : b) {
            sumI += c.i;
            sumJ += c.j;
        }
        Cell buildingCOM = new Cell(sumI/5, sumJ/5);
        HashMap<HashSet<Cell>, Double> potentialPonds = new HashMap<HashSet<Cell>, Double>();

        // generate set of empty adjacent cells to building to be placed **UNUSED
        HashSet<Cell> adjacentEmpty = new HashSet<Cell>();
        for (Cell c : b) {
            Cell abs = new Cell(c.i + buildingPos.i, c.j + buildingPos.j);
            Cell[] adj = abs.neighbors();
            for (Cell a : adj) {
                if (willBeUnoccupied(a, b, buildingPos, land)) {
                    adjacentEmpty.add(a);
                }
            }
        }

        List<Cell> sortedBuildingCells = new Vector<Cell>(); // sorted by low to high i
        for (Cell c : b) {
            sortedBuildingCells.add(c);
        }

        int maxHeight = 0;
        for (Cell p : b) {
            if (p.i > maxHeight) {
                maxHeight = p.i;
            }
        }
        
        Collections.sort(sortedBuildingCells, new Comparator<Cell>() {
                @Override
                public int compare(final Cell c1, final Cell c2) {
                    return c1.i - c2.i;
                }
            });

        HashSet<Cell> newPond = new HashSet<Cell>();
        int pondHeight = 0;
        Cell first = sortedBuildingCells.get(0);
        Cell curr = new Cell(first.i + buildingPos.i, first.j + buildingPos.j + 1);
        boolean toRight = true;
        if (!willBeUnoccupied(curr, b, buildingPos, land)) {
            int minJ = Math.max(first.j + buildingPos.j - 1, 0);
            curr = new Cell(first.i + buildingPos.i, minJ);
            toRight = false;
        }
        
        if (!willBeUnoccupied(curr, b, buildingPos, land)) {
            return newPond;
        } else {
            newPond.add(curr);
            pondHeight++;
        }
        for (int c = 0; c < 4; c++) {
            Cell down = new Cell(curr.i+1, curr.j);
            Cell right = new Cell(curr.i, curr.j+1);
            int minJ = Math.max(curr.j-1, 0);
            Cell left = new Cell(curr.i, minJ);

            if (willBeUnoccupied(down, b, buildingPos, land) && pondHeight == 1) {
                // first cell, try down
                newPond.add(down);
                pondHeight++;
                curr = down;
            } else if (toRight) {
                if (willBeUnoccupied(left, b, buildingPos, land)) {
                    newPond.add(left);
                    curr = left;
                } else if (willBeUnoccupied(right, b, buildingPos, land)) {
                    newPond.add(right);
                    curr = right;
                } else if (willBeUnoccupied(down, b, buildingPos, land)
                           && pondHeight <= maxHeight) {
                    newPond.add(down);
                    curr = down;
                    pondHeight++;
                } else {
                    return newPond;
                }
            } else {
                if (willBeUnoccupied(right, b, buildingPos, land)) {
                    newPond.add(right);
                    curr = right;
                } else if (willBeUnoccupied(left, b, buildingPos, land)) {
                    newPond.add(left);
                    curr = left;
                } else if (willBeUnoccupied(down, b, buildingPos, land)
                           && pondHeight <= maxHeight) {
                    newPond.add(down);
                    curr = down;
                    pondHeight++;
                } else {
                    return newPond;
                } 

            }
        }

        return newPond;
    }

    public boolean hasRoadConnection(Building b, Cell buildingPosition, Land land, Set<Cell> roadCells) {
        Set<Cell> absBuildingCells = new HashSet<Cell>();
        
        for (Cell c : b) {
            Cell abs = new Cell(c.i + buildingPosition.i, c.j + buildingPosition.j);
            absBuildingCells.add(abs);
        }

        for (Cell abs : absBuildingCells) {
            Cell[] absNeighbors = abs.neighbors();
            for (Cell n : absNeighbors) {
                if (n.isRoad())
                    return true;
                if (roadCells.contains(n))
                    return true;
                if (isPerimeter(land, n))
                    return true;
            }
        }
        return false;
    }
    
    public void evaluateMovesAt(int currResArea, int j, Building request,
                                Land land, Set<Cell> roadCells,
                                HashMap<Move, Integer> potentialMoves) {
        // evaluate each rotation in this build spot
        for (int r = 0; r < request.rotations().length; r++) {
            Building b = request.rotations()[r];

            // ignore rotations that are greater than width 3
            int maxWidth = 0;
            for (Cell p : b) {
                if (p.j > maxWidth) {
                    maxWidth = p.j;
                }
            }
            if (maxWidth >= 3) {
                continue;
            }
            
            // ignore rotations that are less than height 3
            int maxHeight = 0;
            for (Cell p : b) {
                if (p.i > maxHeight) {
                    maxHeight = p.i;
                }
            }
            if (maxHeight < 2) {
                continue;
            }

            int i;
            if (currResArea % 2 == 0) {
                i = (currResArea / 2) * 8;
            } else {
                i = ((currResArea + 1) / 2) * 8 - 2 - (maxHeight);
            }
            Cell buildPosition = new Cell(i, j);
            
            // generate score for this rotation and add to potential moves
            if (land.buildable(b, buildPosition)
                && hasRoadConnection(b, buildPosition, land, roadCells)) {
                int score = BASE_RESIDENCE_SCORE;
                Set<Cell> pondCells = new HashSet<Cell>();
                Set<Cell> fieldCells = new HashSet<Cell>();
                boolean hasPond = false;
                boolean hasField = false;
                
                score -= getPackingFactor(b, buildPosition, land);
                if (adjacentPond(b, buildPosition, land)) {
                    hasPond = true;
                    score += POND_BONUS_SCORE;
                }
                if (adjacentField(b, buildPosition, land)) {
                    hasField = true;
                    score += FIELD_BONUS_SCORE;
                }

                // base move without adding any fields or ponds
                Move potential = new Move(true, request, buildPosition, r, roadCells,
                                          new HashSet<Cell>(), new HashSet<Cell>());
                potentialMoves.put(potential, score);

                // now try adding ponds or fields as a potential move
                if (hasPond == false) {
                    pondCells = buildPackedPondField(b, buildPosition, land);
                    potential = new Move(true, request, buildPosition, r, roadCells, pondCells, new HashSet<Cell>());
                    potentialMoves.put(potential, score - 20);
                } else if (hasField == false) {
                    fieldCells = buildPackedPondField(b, buildPosition, land);
                    potential = new Move(true, request, buildPosition, r, roadCells, new HashSet<Cell>(), fieldCells);
                    potentialMoves.put(potential, score - 20);
                }
            } // end if buildable
        } // end building rotations for loop

    } 
    
    /* For residences, divide up the board into "areas". Split the board with roads
       that are 7 cells apart, and each side of a road is an "area"
     */
    public Move play(Building request, Land land) {
        int maxResAreas = land.side / 8 + 1; // 7 empty cell + 1 road = 8
        HashMap<Move, Integer> potentialMoves = new HashMap<Move, Integer>();
        System.out.println("Request type: " + request.type + " " + request.toString());
        if (request.type == Building.Type.RESIDENCE) {
            HashSet<Cell> roadCells = new HashSet<Cell>();
            
            for (int currResArea = 0; currResArea < resAreaCount; currResArea++) {
                for (int j = 0; j < land.side; j++) {
                    evaluateMovesAt(currResArea, j, request, land, roadCells, potentialMoves);
                }
            }

            // if no moves found, make a new res area
            if (potentialMoves.size() == 0) {
                int roadI = ( (resAreaCount+1)/2 ) * 8 - 1;
                for (int roadJ = 0; roadJ < land.side; roadJ++) {
                    Cell roadCell = new Cell(roadI, roadJ);
                    if (land.unoccupied(roadCell)) {
                        roadCells.add(roadCell);
                    }
                }
                resAreaCount += 2;
                for (int currResArea = 0; currResArea < resAreaCount; currResArea++) {
                    for (int j = 0; j < land.side; j++) {
                        evaluateMovesAt(currResArea, j, request, land, roadCells, potentialMoves);
                    }
                }
            }

            Move bestMove = null;
            Map.Entry<Move, Integer> maxScore = null;
            for (Map.Entry<Move, Integer> entry : potentialMoves.entrySet()) {
                if (maxScore == null || entry.getValue() > maxScore.getValue()) {
                    maxScore = entry;
                    bestMove = entry.getKey();
                }
            }


            if (bestMove == null) {
                return new Move(false);
            } else {
                bestMove.road = roadCells;
                return bestMove;
            }
            
        }
        
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
                if (!road_cells.contains(q) && land.unoccupied(q) && !b.contains(q)) {
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
                } else if (!checked[x.i][x.j] && land.unoccupied(x.i,x.j)) {
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
