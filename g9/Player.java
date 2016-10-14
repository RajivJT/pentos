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
    private int BUILD_ROAD_PENALTY = 5; // penalty for each additional road cell built
    private int BUILD_PARK_PENALTY = 5; // penalty for each additional water/park built
    private int ROAD_ADJ_PENALTY = 2; // penalty for each adjacent road cell
    private int PERIMETER_PENALTY = 5; // penalty for each cell on the perimeter
    private int MIN_POTENTIAL_MOVES = 20; // min # of potential moves in vector before considering looking on the next row
    private int ROAD_ADJ_POND_PENALTY = 5; // penalty for each built road cell next to park/pond
    
    // used for evaluating vector of parks/ponds to be built
    private int PARKPOND_PACKING_BONUS = 10; // bonus for each adjacent empty cell

    // used for scoring factories only
    private int POND_PENALTY = 5; // penalty for adjacent ponds/parks
    private int FACTORY_BONUS = 5; // bonus for adjacent factory cells
    
    private Set<Cell> road_cells;
    private Random gen = new Random();
    private int resHighestI = 0;

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

    /* (Park/Pond, score) tuple
     */
    class ScoredParkPond implements Comparable<ScoredParkPond> {
        public Set<Cell> parkPond;
        public int score;

        public ScoredParkPond(Set<Cell> parkPond, int score) {
            this.parkPond = parkPond;
            this.score = score;
        }

        public boolean equals(ScoredParkPond p) {
            return parkPond == p.parkPond && score == p.score;
        }

        public int compareTo(ScoredParkPond p) {
            return score - p.score;
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
       RESIDENCES: adjacent roads count as being "empty"
       FACTORIES: adjacent roads count towards packing
     */
    private int getPackingFactor(Building b, Cell position, Land land,
                                Set<Cell> markedForConstruction) {
        Set<Cell> emptyNeighbors = new HashSet<Cell>();
        Set<Cell> absBuildingCells = getAbsCells(b, position);
        markedForConstruction.addAll(absBuildingCells);
        Set<Cell> neighbors = new HashSet<Cell>();
        for (Cell c : absBuildingCells) {
            Cell[] cNeighbors = c.neighbors();
            for (Cell n : cNeighbors) {
                neighbors.add(n);
            }
        }

        for (Cell c : neighbors) {
            if (markedForConstruction.contains(c)) {
                continue;
            }
            if (b.type == Building.Type.RESIDENCE) {
                if (land.getCellType(c.i, c.j) == Cell.Type.ROAD || land.unoccupied(c)) {
                    emptyNeighbors.add(c);
                }
            }
            else {
                if (land.unoccupied(c)) {
                    emptyNeighbors.add(c);
                }
            }
        }
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

    /* Counts how many adjacent cells of a type (for factory use only - 
       so not counting cells marked for construction)
     */
    public int numAdjType(Building b, Cell buildingPos, Land land, Cell.Type type) {
        Set<Cell> adjacent = new HashSet<Cell>();
        Set<Cell> absBuildingCells = getAbsCells(b, buildingPos);
        Set<Cell> neighbors = new HashSet<Cell>();

        for (Cell c : absBuildingCells) {
            Cell[] cNeighbors = c.neighbors();
            for (Cell n : cNeighbors) {
                neighbors.add(n);
            }
        }

        for (Cell c : neighbors) {
            if (land.getCellType(c.i, c.j) == type) {
                adjacent.add(c);
            }
        }
        return adjacent.size();
    }

    public Double getDist(Cell a, Cell b) {
        return Math.hypot(a.i - b.i, a.j - b.j);
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
            }
            if (onPerimeter(land, abs))
                return true;
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

    /* Takes a candidate park or pond and scores it considering the move that
       it will be added to. type needs to be Cell.Type.WATER or Cell.Type.PARK
       Score = number of empty cells around the park/pond
       So the more empty neighbors, the higher the score
     */
    private int scoreParkOrPond(Move move, Land land,
                                Set<Cell> candidate, Cell.Type type) {
        Building request = move.request;
        Building b = request.rotations()[move.rotation];
        Cell buildingPos = move.location;
        Set<Cell> absBuildingCells = getAbsCells(b, buildingPos);
        Set<Cell> road = move.road;
        Set<Cell> water = move.water;
        Set<Cell> park = move.park;
        Set<Cell> markedForConstruction = new HashSet<Cell>();
        Set<Cell> cellsToScore = new HashSet<Cell>();
        markedForConstruction.addAll(absBuildingCells);
        if (type == Cell.Type.PARK) {
            markedForConstruction.addAll(water);
            cellsToScore = park;
        }
        else {
            markedForConstruction.addAll(park);
            cellsToScore = water;
        }

        Set<Cell> emptyNeighbors = new HashSet<Cell>();
        Set<Cell> roadNeighbors = new HashSet<Cell>();
        for (Cell c : cellsToScore) {
            Cell[] cNeighbors = c.neighbors();
            for (Cell n : cNeighbors) {
                if (land.unoccupied(n) && !markedForConstruction.contains(n)) {
                    emptyNeighbors.add(n);
                }
                if (land.getCellType(n) == Cell.Type.ROAD || road.contains(n)) {
                    roadNeighbors.add(n);
                }
            }
        }

        int score = emptyNeighbors.size() * PARKPOND_PACKING_BONUS;
        score -= roadNeighbors.size() * ROAD_ADJ_POND_PENALTY;
        return score;
    }

    /* returns a set of possible horizontal and vertical parks/ponds (size 4)
       given a move. For the given type, the move MUST have no cells of that type
       under construction
     */
    private Set<Set<Cell>> getHorizVertPermuts(Move move, Land land, Cell.Type type) {
        if (type != Cell.Type.PARK && type != Cell.Type.WATER) {
            return new HashSet<Set<Cell>>();
        }
        Set<Set<Cell>> candidates = new HashSet<Set<Cell>>();
        Building request = move.request;
        Building b = request.rotations()[move.rotation];
        Cell buildingPos = move.location;
        Set<Cell> absBuildingCells = getAbsCells(b, buildingPos);
        Set<Cell> road = move.road;
        Set<Cell> park = move.park;
        Set<Cell> water = move.water;
        Set<Cell> markedForConstruction = new HashSet<Cell>();
        markedForConstruction.addAll(road);
        markedForConstruction.addAll(absBuildingCells);
        if (type == Cell.Type.PARK) {
            markedForConstruction.addAll(water);
        }
        else {
            markedForConstruction.addAll(park);
        }

        // get empty neighbors to building
        Set<Cell> neighbors = new HashSet<Cell>();
        for (Cell c : absBuildingCells) {
            Cell[] cNeighbors = c.neighbors();
            for (Cell n : cNeighbors) {
                if (land.unoccupied(n) && !markedForConstruction.contains(n)) {
                    neighbors.add(n);
                }
            }
        }

        // for each empty neighbor, try to build horizontal and vertical park/pond
        // with length of 4
        for (Cell c : neighbors) {
            // search all 4 directions
            for (int dir = 0; dir < 4; dir++) {
                Set<Cell> candidate = new HashSet<Cell>();
                int length = 4;
                int i = c.i;
                int j = c.j;
                while (length > 0) {
                    if (!land.unoccupied(i, j)) {
                        break;
                    }
                    Cell curr = new Cell(i, j);
                    if (markedForConstruction.contains(curr)) {
                        break;
                    }
                    candidate.add(curr);
                    length--;
                    if (dir == 0) {
                        i--; // grow north
                    } else if (dir == 1) {
                        j++; // east
                    } else if (dir == 2) {
                        i++; // south
                    } else {
                        j--; // west
                    }
                } // end while length > 0
                if (candidate.size() == 4) {
                    candidates.add(candidate);
                }
            } // end for each direction
        } // end for each neighbor cell

        return candidates;
    }

    /* build parks and ponds to a move that currently has none to be built
     */
    private Move buildParksPonds(Move move, Land land) {
        Building request = move.request;
        Building b = request.rotations()[move.rotation];
        Cell buildingPos = move.location;
        Set<Cell> absBuildingCells = getAbsCells(b, buildingPos);
        Set<Cell> road = move.road;
        Set<Cell> markedForConstruction = new HashSet<Cell>();
        markedForConstruction.addAll(road);

        // make sure move doesnt have any water or park to be built
        Set<Cell> park = new HashSet<Cell>();
        Set<Cell> water = new HashSet<Cell>();
        move.park = park;
        move.water = water;
        
        boolean hasField = false;
        boolean hasPond = false;
        hasField = adjacentField(b, buildingPos, land, new HashSet<Cell>());
        hasPond = adjacentPond(b, buildingPos, land, new HashSet<Cell>());

        // if not placed next to a field, try to connect to one or build one
        if (!hasField) {
            Set<Cell> connectPark = connectTo(b, buildingPos, land,
                                              markedForConstruction, Cell.Type.PARK, 3);
            if (connectPark.size() > 0) {
                park.addAll(connectPark);
            }
            else {
                // generate candidate parks
                Set<Set<Cell>> candidateParks = getHorizVertPermuts(move, land, Cell.Type.PARK);

                // score the candidates
                Iterator<Set<Cell>> candParks_it = candidateParks.iterator();
                Vector<ScoredParkPond> scoredParks = new Vector<ScoredParkPond>();
                while (candParks_it.hasNext()) {
                    Set<Cell> candidate = candParks_it.next();
                    int score = scoreParkOrPond(move, land,
                                                candidate, Cell.Type.PARK);
                    scoredParks.add(new ScoredParkPond(candidate, score));
                }

                // sort the scored parks and get the highest
                if (scoredParks.size() == 0) {
                    park = new HashSet<Cell>();
                }
                else {
                    Collections.sort(scoredParks);
                    ScoredParkPond bestScoredPark = scoredParks.lastElement();
                    park = bestScoredPark.parkPond;
                }
            } // end else build a new field
        } // end if !hasField

        // add newly built (or none) park cells to be marked under construction
        markedForConstruction.addAll(park);

        // update the move with park cells
        move.park = park;
        
        // if not placed next to a pond, try to connect to one or build one
        if (!hasPond) {
            Set<Cell> connectWater = connectTo(b, buildingPos, land,
                                               markedForConstruction, Cell.Type.WATER, 3);
            if (connectWater.size() > 0) {
                water.addAll(connectWater);
            }
            else {
                // generate candidate ponds
                Set<Set<Cell>> candidatePonds = getHorizVertPermuts(move, land, Cell.Type.WATER);

                // score the candidates
                Iterator<Set<Cell>> candPonds_it = candidatePonds.iterator();
                Vector<ScoredParkPond> scoredPonds = new Vector<ScoredParkPond>();
                while (candPonds_it.hasNext()) {
                    Set<Cell> candidate = candPonds_it.next();
                    int score = scoreParkOrPond(move, land, candidate, Cell.Type.WATER);
                    scoredPonds.add(new ScoredParkPond(candidate, score));
                }

                // sort the scored ponds and get the highest
                if (scoredPonds.size() == 0) {
                    water = new HashSet<Cell>();
                }
                else {
                    Collections.sort(scoredPonds);
                    ScoredParkPond bestScoredPond = scoredPonds.lastElement();
                    water = bestScoredPond.parkPond;
                }
            } // end else build a new pond
        } // end if !hasPond

        // update move with water cells
        move.water = water;
        return move;
    }
    
    /* Scores moves
     */
    private int scoreMove(Move move, Land land) {
        int score = 0;
        Building request = move.request;
        Building b = request.rotations()[move.rotation];
        Cell buildingPos = move.location;
        Set<Cell> absBuildingCells = getAbsCells(b, buildingPos);
        Set<Cell> road = move.road;
        Set<Cell> water = move.water;
        Set<Cell> park = move.park;
        Set<Cell> markedForConstruction = new HashSet<Cell>();
        //markedForConstruction.addAll(road);
        markedForConstruction.addAll(water);
        markedForConstruction.addAll(park);
        
        score = b.size() * BASE_BUILDING_SCORE;
        if (request.type == Building.Type.RESIDENCE) {
            score -= getPackingFactor(b, buildingPos, land, markedForConstruction);
        }
        else {
            markedForConstruction.addAll(road);
            score -= getPackingFactor(b, buildingPos, land, markedForConstruction);            
        }

        // residences: bonus to parks/ponds, subject to penalty per additional cell built
        if (request.type == Building.Type.RESIDENCE) {
            if (adjacentPond(b, buildingPos, land, water)) {
                score += POND_BONUS_SCORE;
            }

            if (adjacentField(b, buildingPos, land, park)) {
                score += FIELD_BONUS_SCORE;
            }
            score -= (water.size() + park.size()) * BUILD_PARK_PENALTY;
        }

        // factories: penalty for adjacency to parks/ponds, bonus for factory adjacency
        if (request.type == Building.Type.FACTORY) {
            score -= numAdjType(b, buildingPos, land, Cell.Type.WATER) * POND_PENALTY;
            score -= numAdjType(b, buildingPos, land, Cell.Type.PARK) * POND_PENALTY;
            score += numAdjType(b, buildingPos, land, Cell.Type.FACTORY) * FACTORY_BONUS;
        }
        
        score -= road.size() * BUILD_ROAD_PENALTY;
        score -= numAdjRoad(b, buildingPos, land, road) * ROAD_ADJ_PENALTY;

        int cellsOnPerimeter = countPerimeterCells(land, absBuildingCells);
        cellsOnPerimeter += countPerimeterCells(land, road);
        cellsOnPerimeter += countPerimeterCells(land, water);
        cellsOnPerimeter += countPerimeterCells(land, park);
        score -= cellsOnPerimeter * PERIMETER_PENALTY;

        // check how many built road cells are next to park/pond
        int roadCellsAdjParkPond = countRoadAdjParkPond(road, land, water, park);
        score -= roadCellsAdjParkPond * ROAD_ADJ_POND_PENALTY;
        
        // basic final check to heavily penalize cutting off large amounts of free cells
        int numCellsCutOff = countCellsCutOff(move, land);
        if (numCellsCutOff > 20) {
            score -= Math.pow(2, 20);
        }
        else {
            score -= Math.pow(2, numCellsCutOff);
        }

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
        } // end building rotations for loop
    } // end evaluateMovesAt
    
    /* For each request, search entire board and evaluate moves at each cell
       Add potential scored moves to a vector, and choose the best option to play
       Revert to default player if no options found
     */
    public Move play(Building request, Land land) {
        //System.out.println("Request type: " + request.type + " " + request.toString());
        Vector<ScoredMove> potentialMoves = new Vector<ScoredMove>();
        Move nextMove = null;
        
        if (request.type == Building.Type.RESIDENCE) {
            for (int i = 0; i < land.side; i++) {
                for (int j = 0; j < land.side; j++) {
                    evaluateMovesAt(i, j, request, land, potentialMoves);  
                }

                if (i >= resHighestI && potentialMoves.size() >= MIN_POTENTIAL_MOVES) {
                    break; // searched thru constrained space and found enough moves
                }
            }
            
        }
        else { // FACTORIES
            for (int i = land.side-1; i >= 0; i--) {
                for (int j = land.side-1; j >= 0; j--) {
                    evaluateMovesAt(i, j, request, land, potentialMoves);  
                }
                if (potentialMoves.size() >= MIN_POTENTIAL_MOVES) {
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
                            nextMove = chosen;
                        }
                    }
                }
            }
            nextMove = new Move(false); // default player failed to find a spot
        } else {
            // get the move with highest score from Vector potentialMoves
            Collections.sort(potentialMoves);
            //System.out.println("Potential moves: " + potentialMoves.size());
            ScoredMove bestScoredMove = potentialMoves.lastElement();
            Move bestMove = bestScoredMove.move;
            Building b = bestMove.request.rotations()[bestMove.rotation];
            Set<Cell> absBuildingCells = getAbsCells(b, bestMove.location);
            road_cells.addAll(bestMove.road);
            nextMove = bestMove;
        }

        // if residence, update highest i that residences have reached
        if (nextMove.accept && request.type == Building.Type.RESIDENCE) {
            int moveI = nextMove.location.i;
            resHighestI = Math.max(resHighestI, moveI);
            resHighestI = Math.min(resHighestI, land.side-1);
        }

        return nextMove;
    } // end play()

    /* For a given set of cells, counts how many are on the perimeter
     */
    private int countPerimeterCells(Land land, Set<Cell> cells) {
        int count = 0;
        for (Cell c : cells) {
            if (onPerimeter(land, c)) {
                count++;
            }
        }
        return count;
    }
    
    /* Returns if a cell is on perimeter or not
     */
    private boolean onPerimeter(Land land, Cell cell) {
        int i = cell.i;
        int j = cell.j;
        return (i <= 0 || j <= 0 || i >= land.side-1 || j >= land.side-1);
    }

    /* Returns if a group of cells has a road connection (either existing road
       or one marked for construction)
     */
    private boolean isConnectedToRoad(Set<Cell> group, Land land,
                                      Set<Cell> roadMarkedForConstruction) {
        Set<Cell> neighbors = new HashSet<Cell>();
        for (Cell c : group) {
            Cell[] cNeighbors = c.neighbors();
            for (Cell n : cNeighbors) {
                neighbors.add(n);
            }
            if (onPerimeter(land, c)) {
                return true;
            }
        }

        for (Cell c : neighbors) {
            if (land.getCellType(c.i, c.j) == Cell.Type.ROAD ||
                roadMarkedForConstruction.contains(c)) {
                return true;
            }
        }
        
        return false;
    }
    
    /* Returns set of empty cells that are connected to this empty cell
     */
    private Set<Cell> getConnectedEmptyCells(Cell c, Land land,
                                             Set<Cell> markedForConstruction) {
        Set<Cell> emptyCellGroup = new HashSet<Cell>();
        Set<Cell> visited = new HashSet<Cell>();
        Stack<Cell> stack = new Stack<Cell>();
        c.previous = c;
        stack.push(c);
        visited.add(c);

        while (!stack.empty()) {
            Cell curr = stack.pop();
            emptyCellGroup.add(curr);
            visited.add(curr);
            Cell[] neighbors = curr.neighbors();
            for (Cell n : neighbors) {
                if (visited.contains(n)) {
                    continue;
                }

                if (land.unoccupied(n) && !markedForConstruction.contains(n)) {
                    stack.push(n);
                }
            }
        } // end while !stack.empty()
        return emptyCellGroup;
    }
    
    /* Counts how many cells are cut off from road connection as a result of
       given move
     */
    private int countCellsCutOff(Move move, Land land) {
        Building request = move.request;
        Building b = request.rotations()[move.rotation];
        Cell buildingPos = move.location;
        Set<Cell> absBuildingCells = getAbsCells(b, buildingPos);
        Set<Cell> road = move.road;
        Set<Cell> water = move.water;
        Set<Cell> park = move.park;
        Set<Cell> markedForConstruction = new HashSet<Cell>();
        markedForConstruction.addAll(absBuildingCells);
        markedForConstruction.addAll(road);
        markedForConstruction.addAll(water);
        markedForConstruction.addAll(park);
        Set<Cell> neighbors = new HashSet<Cell>();

        for (Cell c : markedForConstruction) {
            Cell[] cNeighbors = c.neighbors();
            for (Cell n : cNeighbors) {
                if (!markedForConstruction.contains(n)) {
                    neighbors.add(n);
                }
            }
        }

        // for each empty neighbor, get the group of empty cells connected to it
        Set<Set<Cell>> emptyCellGroups = new HashSet<Set<Cell>>();
        Set<Set<Cell>> unconnectedGroups = new HashSet<Set<Cell>>();
        for (Cell c : neighbors) {
            if (land.unoccupied(c) && !markedForConstruction.contains(c)) {
                Set<Cell> group = getConnectedEmptyCells(c, land, markedForConstruction);
                emptyCellGroups.add(group);
            }
        }

        // for each empty cell group, check if it's connected
        Iterator<Set<Cell>> group_it = emptyCellGroups.iterator();
        while (group_it.hasNext()) {
            Set<Cell> group = group_it.next();
            if (!isConnectedToRoad(group, land, road)) {
                unconnectedGroups.add(group);
            }
        }

        // for each unconnected empty cell group, sum up the cells
        group_it = unconnectedGroups.iterator();
        int unconnectedCount = 0;
        while (group_it.hasNext()) {
            Set<Cell> group = group_it.next();
            unconnectedCount += group.size();
        }
        
        return unconnectedCount;
    }

    int countRoadAdjParkPond(Set<Cell> road, Land land, Set<Cell> water, Set<Cell> park) {
        Set<Cell> roadCellsAdj = new HashSet<Cell>();
        Set<Cell> neighbors = new HashSet<Cell>();

        for (Cell r : road) {
            Cell[] rNeighbors = r.neighbors();
            for (Cell n : rNeighbors) {
                neighbors.add(n);
            }
        }

        for (Cell n : neighbors) {
            if (land.getCellType(n) == Cell.Type.WATER ||
                land.getCellType(n) == Cell.Type.PARK ||
                water.contains(n) || park.contains(n)) {
                roadCellsAdj.add(n);
            }
        }

        return roadCellsAdj.size();
    }
    
    /* Searches for a cell of specified type that is up to specified distance 
       away from building. Returns set of cells that satisfies this, or an empty 
       set if none found
    */
    private Set<Cell> connectTo(Building b, Cell buildingPos, Land land,
                                Set<Cell> markedForConstruction, Cell.Type type,
                                int maxDistance) {
        // only works for parks and fields
        if (type != Cell.Type.WATER && type != Cell.Type.PARK) {
            return new HashSet<Cell>();
        }
 
        Set<Cell> absBuildingCells = getAbsCells(b, buildingPos);
        Queue<Cell> queue = new LinkedList<Cell>();
         
        for (Cell c : absBuildingCells) {
            Cell[] neighbors = c.neighbors();
            for (Cell n : neighbors) {
                if (land.unoccupied(n) && !markedForConstruction.contains(n)) {
                    queue.add(n);
                }
            }
        }
 
        int distance = maxDistance;
        int currIterSize = queue.size(); // keep track of how many cells in current search depth
        Set<Cell> connectingCells = new HashSet<Cell>();
        Set<Cell> visited = new HashSet<Cell>();
 
        while (queue.size() > 0) {
            if (currIterSize == 0) {
                // if done with current search depth, get size of next search depth and increase
                // search depth counter
                currIterSize = queue.size();
                distance--;
                if (distance <= 0) {
                    break;
                }
            }
 
            Cell curr = queue.remove();
            visited.add(curr);
            Cell[] neighbors = curr.neighbors();
            Cell found = null;
            for (Cell c : neighbors) {
                if (type == Cell.Type.WATER && land.isPond(c)) {
                    found = curr;
                    break;
                }
 
                if (type == Cell.Type.PARK && land.isField(c)) {
                    found = curr;
                    break;
                }
 
                if ( land.unoccupied(c) && !absBuildingCells.contains(c)
                     && !markedForConstruction.contains(c) && !visited.contains(c) ) {
                    Cell next = new Cell(c.i, c.j, curr);
                    queue.add(next);
                }
            }
 
            if (found != null) {
                while (found != null) {
                    connectingCells.add(found);
                    found = found.previous;
                }
                break;
            }
            currIterSize--;
        } // end while queue.size() > 0
        return connectingCells;
    } // end connectTo()

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


