package backend.common;

import java.math.BigDecimal;

public class PlayerBalance {
    BigDecimal balance;

    public PlayerBalance(BigDecimal balance){
        if(balance==null){
            throw new IllegalArgumentException("Balance should not be null");
        }
        if(balance.compareTo(BigDecimal.ZERO) <0){
            throw new IllegalArgumentException("Balance should be >=0");
        }

        this.balance=balance;
    }

    public synchronized void addBalance(BigDecimal amount){
        if (amount == null) throw new IllegalArgumentException("Adding amount should not be null!");
        if(amount.compareTo(BigDecimal.ZERO)<0) throw new IllegalArgumentException("Adding amount should be > 0");
        this.balance = this.balance.add(amount);
    }

    public synchronized void removeBalance(BigDecimal amount){
        if (amount == null) throw new IllegalArgumentException("The removable amount should not be null!");
        this.balance =this.balance.subtract(amount);
    }

    public synchronized BigDecimal getBalance() {
        return this.balance;
    }


}
