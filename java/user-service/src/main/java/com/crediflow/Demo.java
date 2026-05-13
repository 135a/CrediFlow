package com.crediflow;

import java.util.concurrent.CountDownLatch;

public class Demo {

    public static void main(String []args)  {
        CountDownLatch c=new CountDownLatch(3);
        int t=0;
        Thread t1=new Thread(
                ()->{
                    while(t%3==0){
                        System.out.println("A");
                    }

                }
        );
        Thread t2=new Thread(
                ()->{
                    while(t%3==1){
                        System.out.println("B");
                    }
                }
        );
        Thread t3=new Thread(
                ()->{
                    while(t%3==2){
                        System.out.println("C");
                    }
                }
        );



    }


}
