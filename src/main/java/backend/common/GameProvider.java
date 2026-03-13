package backend.common;

import java.util.Objects;

public class GameProvider {

    private final String name;

    public GameProvider(String name){
        if(name==null){
            this.name="";
        }else {
            this.name=name.trim().toLowerCase();
        }

    }

    // Getters
    public String getName(){return this.name;}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameProvider)) return false;
        GameProvider that = (GameProvider) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

}
