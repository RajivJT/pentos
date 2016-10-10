package pentos.sim;

public interface Sequencer {

    public void init(Long seed);
    public Building next();

}
