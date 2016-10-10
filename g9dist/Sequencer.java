package pentos.g9dist;

import pentos.sim.Building;
import pentos.sim.Cell;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class Sequencer implements pentos.sim.Sequencer {

    private Random gen;
    private final double ratio = 0.5; // ratio of residences to total number of buildings
    
    private int factnum = 1;

   


    private static final int[][][] tetris = {
    	{{1,0}, {1,1}, {1,2}, {0,1}, {2,1}}, //plus
    	{{0,0}, {0,1}, {0,2}, {0,3}, {1,3}}, //ell
    	{{0,0}, {1,0}, {2,0}, {2,1}, {2,2}}, //tee
    	{{0,0}, {0,1}, {0,2}, {0,3}, {1,0}}, //hangman
    	{{0,0}, {0,1}, {1,1}, {2,1}, {2,0}}, //u
    	{{0,0}, {0,1}, {0,2}, {1,2}, {1,1}}, //thumbsup
    	{{0,0}, {0,1}, {0,2}, {0,3}, {0,4}}, //line
    	{{0,1}, {1,1}, {2,1}, {2,0}, {3,0}}, //lightningbolt
    	{{0,0}, {0,1}, {0,2}, {1,2}, {2,2}}}; //bend
    private int count=0;

    public void init() {
        count++;
    }

    public void init(Long seed) {

    }

    public Building next() {
    	gen = new Random();
    	if (gen.nextDouble() > ratio){
	    return randomFactory();
		}
		else{
		return randomRes();
        
    	}
    }

    private Building randomFactory() { // random rectangle
	Set<Cell> factory = new HashSet<Cell>();
	for (int i=1; i<=factnum; i++) {
	    for (int j=1; j<=factnum; j++) {
		factory.add(new Cell(i,j));
	    }
	}
	factnum++;
	if (factnum>5) {
		factnum=1;
	}
	return new Building(factory.toArray(new Cell[factory.size()]), Building.Type.FACTORY);
    }

    private Building randomRes(){
    	Set<Cell> res = new HashSet<Cell>();
    	count++;
    	if (count>8) {
        	count=0;
        }
        for(int[] shape : tetris[count]) {
            res.add(new Cell(shape[0], shape[1]));
        }
        
        return new Building(res.toArray(new Cell[res.size()]), Building.Type.RESIDENCE);
    }    
}
