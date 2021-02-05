package com.sumkor.demo.test;

import org.apache.dubbo.common.utils.AtomicPositiveInteger;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * @author Sumkor
 * @since 2021/2/5
 */
public class AtomicIntegerFieldUpdaterTest {

    /**
     * AtomicIntegerFieldUpdater说明
     * 基于反射的实用工具，可以对指定类的指定 volatile int 字段进行原子更新。此类用于原子数据结构，
     * 该结构中同一节点的几个字段都独立受原子更新控制。
     * 注意，此类中 compareAndSet 方法的保证弱于其他原子类中该方法的保证。
     * 因为此类不能确保所有使用的字段都适合于原子访问目的，所以对于相同更新器上的 compareAndSet 和 set 的其他调用，
     * 它仅可以保证原子性和可变语义。
     */
    @Test
    public void compareAndSet() {
        AtomicIntegerFieldUpdater<Person> mAtoLong = AtomicIntegerFieldUpdater.newUpdater(Person.class, "id");
        Person person = new Person(12345);
        mAtoLong.compareAndSet(person, 12345, 1000);
        System.out.println("id = " + person.getId()); // 1000
    }

    /**
     * @see AtomicPositiveInteger#getAndIncrement()
     */
    @Test
    public void getAndIncrement() {
        AtomicIntegerFieldUpdater<Person> mAtoLong = AtomicIntegerFieldUpdater.newUpdater(Person.class, "id");
        Person person = new Person(12345);
        mAtoLong.getAndIncrement(person);
        System.out.println("id = " + person.getId()); // 12346
    }

    /**
     * 对于字段ID
     * 1）用 volatile 修饰，必须是基本类型数据，不能是包装类型
     * 2）必须是实例变量，不可以是类变量
     * 3）必须是可变的变量，不能是 final 修饰的变量
     */
    class Person {
        volatile int id;

        public Person(int id) {
            this.id = id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }

}
