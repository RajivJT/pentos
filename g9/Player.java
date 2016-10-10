package pentos.g9;

import pentos.sim.Cell;
import pentos.sim.Building;
import pentos.sim.Land;
import pentos.sim.Move;

import java.util.*;

public class Player implements pentos.sim.Player {

    private int BASE_BUILDING_SCORE = 10; // base score for a single cell of a building
    private int PACKING_FACTOR_MULTIPLE = 10; // score multiple for each adjacent cell
    private int POND_BONUS_SCORE = 20; // score to add for a pond
    private int FIELD_BONUS_SCORE = 20; // score to add for a field
    private int BUILD_ROAD_PENALTY = 50; // penalty for each additional road cell built
    private int BUILD_PARK_PENALTY = 4; // penalty for each additional water/park built
    private int ROAD_ADJ_PENALTY = 5; // penalty for each adjacent road cell
    private int MIN_POTENTIAL_MOVES = 4; // min # of potential moves in vector before considering looking on the next row
    private int BLOCK_SIZE = 7;
    private Set<Cell> road_cells;
    private Random gen = new Random();
    

    /* (Move, score) tuple
     */
    class ScoredMove implements Comparable<ScoredMove> {
        public Move move;
        public int score;

        public ScoredMove(Move move, int score) {
            this.move = move;
            this.score = score;
        }

        public boolean equals(ScoredMove m) {
            return move == m.move && score == m.score;
        }

        public int compareTo(ScoredMove m) {
            return score - m.score;
        }
    }

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

            Set<Cell> roadCells = findShortestRoadG1(shiftedCells, land);

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


    /* Returns number of adjacent empty cells (how well packed the building is)
       RESIDENCES: takes into account road, water, park cells about to be built
     */
    private int getPackingFactor(Building b, Cell position, Land land,
                                Set<Cell> markedForConstruction) {
        Set<Cell> emptyNeighbors = new HashSet<Cell>();
        Set<Cell> absBuildingCells = getAbsCells(b, position);

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
                        // last check: if n is NOT one of the road/water/park to be constructed
                        if (!markedForConstruction.contains(n)) {
                            emptyNeighbors.add(n);
                        }
                    }
                } // end if land.unoccupied(n)
            } // end for(Cell n : absNeighbors)
        } // end for(Cell abs : absBuildingCells)
        return emptyNeighbors.size() * PACKING_FACTOR_MULTIPLE;
    }

    /* Checks if building to be placed is adjacent to a pond (existing or 
       under construction)
     */
    public boolean adjacentPond(Building b, Cell position, Land land,
                                Set<Cell> water) {
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
            for (Cell w : water) {
                if (w.equals(p)) {
                    return true;
                }
            }
        }
        return false;
    }

    /* Checks if building to be placed is adjacent to a field
     */
    public boolean adjacentField(Building b, Cell position, Land land,
                                 Set<Cell> park) {
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
            for (Cell f : park) {
                if (f.equals(p)) {
                    return true;
                }
            }
        }
        return false;
    }

    /* Counts how many adjacent road cells
     */
    public int numAdjRoad(Building b, Cell position, Land land,
                              Set<Cell> road) {
        Set<Cell> adjRoadCells = new HashSet<Cell>();
        Set<Cell> adjacentPoints = new HashSet<Cell>();
        for (Cell c : b) {
            Cell abs = new Cell(c.i + position.i, c.j + position.j);
            Cell[] adj = abs.neighbors();
            for (Cell a : adj) {
                adjacentPoints.add(a);
            }
        }

        for (Cell p : adjacentPoints) {
            if (p.i < 0 || p.j < 0 || p.i >= land.side || p.j >= land.side) {
                adjRoadCells.add(p);
            }
            else if (road_cells.contains(p)) {
                adjRoadCells.add(p);
            }
            else if (road.contains(p)) {
                adjRoadCells.add(p);
            }
        }
        return adjRoadCells.size();
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

        // This is a cell that will eventually be occupied by a road
        if ((c.i + 1) % (BLOCK_SIZE + 1) == 0)
            return false;

        return true; // will be unoccupied
    }

    public Double getDist(Cell a, Cell b) {
        return Math.hypot(a.i - b.i, a.j - b.j);
    }

    /*
      Adds either a park or a field to a Move - we use the word "development" to refer to either
      of these entities.
     */
    public Move buildWithDevelopment(Cell buildingPos, Building request, int rotation,
                                     Set<Cell> roadCells, Land land) {
        // Create a set that holds the cells that the building will occupy
        Set<Cell> shiftedCells = new HashSet<Cell>();
        Building b = request.rotations()[rotation];
        for (Cell cell : b) {
            shiftedCells.add(new Cell(cell.i + buildingPos.i, cell.j + buildingPos.j));
        }

        // Create a set of cells for the new development
        Set<Cell> developmentCells = new HashSet<Cell>();

        // TODO: Right now we're just selecting the last valid candidate development and building
        // it. We probably want to store each valid candidate in a list and score them so that
        // we can choose the best candidate instead.
        for (Cell cell : shiftedCells) {
            Cell[] adj = cell.neighbors();
            // Loop through all cells neighboring the building
            for (Cell neighbor : adj) {
                int i = neighbor.i;
                int j = neighbor.j;

                // Attempt to construct a horizontal development
                Set<Cell> candidateDevelopment = new HashSet<Cell>();
                boolean candidateIsValid = true;

                if (j+3 >= land.side)
                    candidateIsValid = false;

                for (int dj = 0; candidateIsValid && dj < 4; dj++) {
                    // Starting from a cell that neighbors a building, build to the right
                    Cell candidateCell = new Cell(i+dj, j);
                    // If the current cell is being used for something else, this candidate
                    // is invalid
                    if (!willBeUnoccupied(candidateCell, b, buildingPos, land) ||
                        roadCells.contains(candidateCell)) {
                        candidateIsValid = false;
                        break;
                    }
                    candidateDevelopment.add(candidateCell);
                }

                // If we couldn't construct a development by building rightward, build leftward
                // instead
                if (!candidateIsValid) {
                    candidateIsValid = true;
                    candidateDevelopment = new HashSet<Cell>();

                    if (j-3 < 0)
                        candidateIsValid = false;

                    for (int dj = 0; candidateIsValid && dj < 4; dj++) {
                        // Build to the left
                        Cell candidateCell = new Cell(i, j-dj);

                        // If the current cell is being used for something else, this candidate
                        // is invalid
                        if (!willBeUnoccupied(candidateCell, b, buildingPos, land) ||
                            roadCells.contains(candidateCell)) {
                            candidateIsValid = false;
                            break;
                        }
                        candidateDevelopment.add(candidateCell);
                    }
                }

                // If we found a valid candidate this round, mark it as the development to be
                // built
                if (candidateIsValid) {
                    developmentCells = candidateDevelopment;
                }
            }
        }

        // If the building doesn't have a pond or field, choose one randomly and build it
        return new Move(true, request, buildingPos, rotation, roadCells,
                        new HashSet<Cell>(), developmentCells);
        /*if (!hasPond && !hasField) {
            int choice = gen.nextInt(2);
            if (choice == 0) {
                return new Move(true, request, buildingPos, rotation, roadCells,
                                new HashSet<Cell>(), developmentCells);
            } else {
                return new Move(true, request, buildingPos, rotation, roadCells, developmentCells,
                                new HashSet<Cell>());
            }
        } else if (hasPond) {
            // Build a field if there is a pond
            return new Move(true, request, buildingPos, rotation, roadCells, new HashSet<Cell>(),
                            developmentCells);
        } else {
            // Build a pond if there is a field
            return new Move(true, request, buildingPos, rotation, roadCells, developmentCells,
                            new HashSet<Cell>());
        }*/
    }

    /* Checks if building to be placed will be connected to a road
       (either already on the board or a part of the roads cells passed in
       as an argument) or not
     */
    public boolean hasRoadConnection(Building b, Cell buildingPosition, Land land,
                                     Set<Cell> roadConstruction ) {
        Set<Cell> absBuildingCells = getAbsCells(b, buildingPosition);
        
        for (Cell abs : absBuildingCells) {
            Cell[] absNeighbors = abs.neighbors();
            for (Cell n : absNeighbors) {
                if (road_cells.contains(n))
                    return true;
                if (roadConstruction.contains(n))
                    return true;
                if (isPerimeter(land, n))
                    return true;
            }
        }
        return false;
    }

    private Set<Cell> getAbsCells(Building b, Cell buildingPos) {
        Set<Cell> absBuildingCells = new HashSet<Cell>();
        for (Cell c : b) {
            Cell abs = new Cell(c.i + buildingPos.i, c.j + buildingPos.j);
            absBuildingCells.add(abs);
        }
        return absBuildingCells;
    }
    
    /* TODO
     */
    private Set<Cell> buildRoad(Set<Cell> building, Land land) {
        return null;
    }
        
    /* TODO: fill out
     */
    private Move buildParksPonds(Move move, Land land) {
        // Create a set that holds the cells that the building will occupy
        Set<Cell> shiftedCells = new HashSet<Cell>();
        Building b = move.request.rotations()[move.rotation];
        for (Cell cell : b) {
            shiftedCells.add(new Cell(cell.i + move.location.i, cell.j + move.location.j));
        }

        // Create a set of cells for the new development
        Set<Cell> developmentCells = new HashSet<Cell>();

        // TODO: Right now we're just selecting the last valid candidate development and building
        // it. We probably want to store each valid candidate in a list and score them so that
        // we can choose the best candidate instead.
        for (Cell cell : shiftedCells) {
            Cell[] adj = cell.neighbors();
            // Loop through all cells neighboring the building
            for (Cell neighbor : adj) {
                int i = neighbor.i;
                int j = neighbor.j;

                if (i != move.location.i)
                    continue;

                // Attempt to construct a horizontal development
                Set<Cell> candidateDevelopment = new HashSet<Cell>();
                boolean candidateIsValid = true;

                if (j+3 >= land.side)
                    candidateIsValid = false;

                for (int dj = 0; candidateIsValid && dj < 4; dj++) {
                    // Starting from a cell that neighbors a building, build to the right
                    Cell candidateCell = new Cell(i+dj, j);
                    // If the current cell is being used for something else, this candidate
                    // is invalid
                    if (!willBeUnoccupied(candidateCell, b, move.location, land) ||
                        move.road.contains(candidateCell)) {
                        candidateIsValid = false;
                        break;
                    }
                    candidateDevelopment.add(candidateCell);
                }

                // If we couldn't construct a development by building rightward, build leftward
                // instead
                if (!candidateIsValid) {
                    candidateIsValid = true;
                    candidateDevelopment = new HashSet<Cell>();

                    if (j-3 < 0)
                        candidateIsValid = false;

                    for (int dj = 0; candidateIsValid && dj < 4; dj++) {
                        // Build to the left
                        Cell candidateCell = new Cell(i, j-dj);

                        // If the current cell is being used for something else, this candidate
                        // is invalid
                        if (!willBeUnoccupied(candidateCell, b, move.location, land) ||
                            move.road.contains(candidateCell)) {
                            candidateIsValid = false;
                            break;
                        }
                        candidateDevelopment.add(candidateCell);
                    }
                }

                // If we couldn't construct a development by building rightward, build leftward
                // instead
                if (!candidateIsValid) {
                    candidateIsValid = true;
                    candidateDevelopment = new HashSet<Cell>();

                    if (j-3 < 0)
                        candidateIsValid = false;

                    for (int dj = 0; candidateIsValid && dj < 4; dj++) {
                        // Build to the left
                        Cell candidateCell = new Cell(i, j+dj);

                        // If the current cell is being used for something else, this candidate
                        // is invalid
                        if (!willBeUnoccupied(candidateCell, b, move.location, land) ||
                            move.road.contains(candidateCell)) {
                            candidateIsValid = false;
                            break;
                        }
                        candidateDevelopment.add(candidateCell);
                    }
                }

                // If we found a valid candidate this round, mark it as the development to be
                // built
                if (candidateIsValid) {
                    developmentCells = candidateDevelopment;
                }
            }
        }

        boolean hasPond = adjacentPond(b, move.location, land, new HashSet<Cell>());
        boolean hasField = adjacentField(b, move.location, land, new HashSet<Cell>());

        if (!hasPond && !hasField) {
            int choice = gen.nextInt(2);
            if (choice == 0) {
                return new Move(true, move.request, move.location, move.rotation, move.road,
                                new HashSet<Cell>(), developmentCells);
            } else {
                return new Move(true, move.request, move.location, move.rotation, move.road, developmentCells,
                                new HashSet<Cell>());
            }
        } else if (hasPond) {
            // Build a field if there is a pond
            return new Move(true, move.request, move.location, move.rotation, move.road, new HashSet<Cell>(),
                            developmentCells);
        } else {
            // Build a pond if there is a field
            return new Move(true, move.request, move.location, move.rotation, move.road, developmentCells,
                            new HashSet<Cell>());
        }
    }

    /* TODO: how many cells get cut off from network?
     */
    
    /* TODO: score differently for factories vs residences
     */
    private int scoreMove(Move move, Land land) {
        int score = 0;
        Building request = move.request;
        Building b = request.rotations()[move.rotation];
        Cell buildingPos = move.location;
        Set<Cell> road = move.road;
        Set<Cell> water = move.water;
        Set<Cell> park = move.park;
        Set<Cell> markedForConstruction = new HashSet<Cell>();
        //markedForConstruction.addAll(road);
        markedForConstruction.addAll(water);
        markedForConstruction.addAll(park);
        
        score = b.size() * BASE_BUILDING_SCORE;
        score -= getPackingFactor(b, buildingPos, land, markedForConstruction);

        if (adjacentPond(b, buildingPos, land, water)) {
            score += POND_BONUS_SCORE;
        }

        if (adjacentField(b, buildingPos, land, park)) {
            score += FIELD_BONUS_SCORE;
        }

        score -= (water.size() + park.size()) * BUILD_PARK_PENALTY;
        score -= road.size() * BUILD_ROAD_PENALTY;

        score -= numAdjRoad(b, buildingPos, land, road) * ROAD_ADJ_PENALTY;
        
        return score;
    }
    
    /* For a given location and request, checks all rotations of the building
       in that location and assigns a score to each, adds it to the vector of
       potential moves
     */
    private void evaluateMovesAt(int i, int j, Building request,
                                 Land land, Vector<ScoredMove> potentialMoves) {
        // evaluate each rotation in this build spot
        for (int r = 0; r < request.rotations().length; r++) {
            Building b = request.rotations()[r];
            Cell buildingPos = new Cell(i, j);

            Set<Cell> absBuildingCells = getAbsCells(b, buildingPos);
            Set<Cell> water = new HashSet<Cell>();
            Set<Cell> park = new HashSet<Cell>();
            Set<Cell> road = new HashSet<Cell>();
            
            if (land.buildable(b, buildingPos)) {
                // TODO: replace this with improved road building algo
                road = findShortestRoadG1(absBuildingCells, land);
                Move potential = new Move(true, request, buildingPos, r, road, water, park);
                if (road == null || !hasRoadConnection(b, buildingPos, land, road)) {
                    continue;
                }
                int score = scoreMove(potential, land);
                ScoredMove sMove = new ScoredMove(potential, score);
                potentialMoves.add(sMove);

                if (request.type == Building.Type.RESIDENCE) {
                    Move potentialPlus = buildParksPonds(potential, land);
                    int scorePlus = scoreMove(potentialPlus, land);
                    ScoredMove sMovePlus = new ScoredMove(potentialPlus, scorePlus);
                    potentialMoves.add(sMovePlus);
                }
            }
            //TODO: call build road algo and call scoring algo

            //TODO: build parks/ponds and call scoring

        } // end building rotations for loop

    } 
    
    /* For each request, search entire board and evaluate moves at each cell
       Add potential scored moves to a vector, and choose the best option to play
       Revert to default player if no options found
     */
    public Move play(Building request, Land land) {
        System.out.println("Request type: " + request.type + " " + request.toString());
        Vector<ScoredMove> potentialMoves = new Vector<ScoredMove>();
        
        if (request.type == Building.Type.RESIDENCE) {
            for (int i = 0; i < land.side; i++) {
                for (int j = 0; j < land.side; j++) {
                    evaluateMovesAt(i, j, request, land, potentialMoves);  
                }
                System.out.println("Potential moves: " + potentialMoves.size());
                if (potentialMoves.size() >= MIN_POTENTIAL_MOVES) {
                    System.out.println("Sufficient moves found at i = " + i);
                    break; // searched thru constrained space and found enough moves
                }
            }

        }
        else {
            for (int i = land.side-1; i >= 0; i--) {
                for (int j = land.side-1; j >= 0; j--) {
                    evaluateMovesAt(i, j, request, land, potentialMoves);  
                }
                System.out.println("Potential moves: " + potentialMoves.size());
                if (potentialMoves.size() >= MIN_POTENTIAL_MOVES) {
                    System.out.println("Sufficient moves found at i = " + i);
                    break; // searched thru constrained space and found enough moves
                }
            }
        }

        // if no potential moves found, try one more run with default player
        if (potentialMoves.size() == 0) {
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
            return new Move(false); // default player failed to find a spot
        } else {
            // get the move with highest score from Vector potentialMoves
            Collections.sort(potentialMoves);
            ScoredMove bestScoredMove = potentialMoves.lastElement();
            Move bestMove = bestScoredMove.move;
            Building b = bestMove.request.rotations()[bestMove.rotation];
            Set<Cell> absBuildingCells = getAbsCells(b, bestMove.location);
            road_cells.addAll(bestMove.road);

            return bestMove;
        }

    } // end play()
    
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


    /* Group 1's improved findShortestRoad algorithm
     */
    private Set<Cell> findShortestRoadG1(Set<Cell> b, Land land) {
	Set<Cell> output = new HashSet<Cell>();
	boolean[][] checked = new boolean[land.side][land.side];
	Queue<Cell> queue = new LinkedList<Cell>();
	// add border cells that don't have a road currently
	Cell source = new Cell(Integer.MAX_VALUE,Integer.MAX_VALUE); // dummy cell to serve as road connector to perimeter cells
	for(Cell x : b) {
		if(x.i==0 || x.i==land.side-1 || x.j==0 || x.j==land.side-1) return new HashSet<Cell>();
		for(Cell y : x.neighbors()) {
			if(road_cells.contains(y)) {
				return new HashSet<Cell>();
			}
		}
	}
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
		    checked[x.i][x.j] = true;
		    queue.add(x);
		} 

	    }
	}
	if (output.isEmpty() && queue.isEmpty())
	    return null;
	else
	    return output;
    } // end findShortestRoadG1


    
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


