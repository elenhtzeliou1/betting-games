package backend.mapReduce;

import java.util.List;

public class mapReduceFramework {



    private static class Pair<K,V>{
        private final K key;
        private final V value;


        public Pair(K key, V value){
            this.key = key;
            this.value = value;
        }

        public K getKey(){return key;}
        public V getValue(){return value;}
    }

    /*
    * Contract for worker's side map phase
    *
    * @param <K> input key type (gameName)
    * @param <V> input value type
    * @param <K2> intermediate key type
    * @param <V2> intermediate value type
    * */

    public interface Mapper<K,V,K2,V2>{
        List<Pair<K2,V2>> map(K key, V value);
    }

}
