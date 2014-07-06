package quichstart;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Hello world!
 *
 */
public class App
{
    private static ReadWriteLock rwl = new ReentrantReadWriteLock();

    private static JedisPool pool = null;
    public static JedisPool PoolInstance(){
        if (pool == null){
            JedisPoolConfig config = new JedisPoolConfig();
            config.setMaxTotal(32);

            config.setJmxEnabled(true);
            config.setJmxNamePrefix("pool");
            pool = new JedisPool(config, "localhost", 6379);
        }

        return pool;
    }

    private static int SleepTime = 50;

    public static void main( String[] args ) throws IOException {
        long start = System.currentTimeMillis();

        Jedis jedis = PoolInstance().getResource();
        try {
            jedis.set("foo", "bar");
        }
        catch (JedisConnectionException ex) {
            if (null != jedis){
                PoolInstance().returnBrokenResource(jedis);
                jedis = null;
            }
        }
        finally{
            if (null != jedis){
                PoolInstance().returnResource(jedis);
            }
        }

        List<Thread> list = new ArrayList<Thread>();
        for(int i = 0; i < 50; i++)
        {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                while(true) {

                    Jedis jedis = PoolInstance().getResource();

                    rwl.readLock().lock();

                    int waitTime = 0;
                    try{
                        waitTime = SleepTime;
                    }finally {
                        rwl.readLock().unlock();
                    }

                    if (waitTime == 0){
                        break;
                    }
                    try {
                        /// ... do stuff here ... for example
                        jedis.set("foo", "bar");
                        String foo = jedis.get("foo");

                    } catch (JedisConnectionException e) {
                        // returnBrokenResource when the state of the object is unrecoverable
                        if (null != jedis) {
                            PoolInstance().returnBrokenResource(jedis);
                            jedis = null;
                        }
                    } finally {
                        /// ... it's important to return the Jedis instance to the pool once you've finished using it
                        if (null != jedis) {
                            try {
                                PoolInstance().returnResource(jedis);
                            } catch (Exception e) {
                                PoolInstance().returnBrokenResource(jedis);
                                jedis = null;
                                e.printStackTrace();
                            }
                        }
                    }

                    try {
                        Thread.sleep(1 * 1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }

                }
            });
            thread.start();
            list.add(thread);
        }





        while(true){
            System.out.println( "Please input sleep time : ");
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

            System.out.println("Enter String");
            String s = br.readLine();

            System.out.println("Enter Integer:");
            int inputInteger = -1;
            try{
                inputInteger = Integer.parseInt(br.readLine());

            }catch(NumberFormatException nfe){
                System.err.println("Invalid Format!");
            }

            if (inputInteger >= 0) {
                rwl.writeLock().lock();
                try {
                    SleepTime = inputInteger;
                } finally {
                    rwl.writeLock().unlock();
                }
            }

            if (inputInteger == 0){
                System.out.printf("exit !!");
                break;
            }
            else {
                System.out.println(inputInteger);
            }


        }

        try
        {
            for(Thread thread : list)
            {
                thread.join();
            }
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        long end = System.currentTimeMillis();
        System.out.println("子线程执行时长：" + (end - start));

        pool.destroy();

        System.out.println("=================== ALL Done !! =====================");
    }
}
