package com.tony;

/**
 * @author jiangwenjie 2020/3/30
 */
public class IntBitCheck {
    private final int BYTE_SIZE = 1 << 5;
    private int[] ints;

    public IntBitCheck(long maxVal) {
        if (maxVal > (long)BYTE_SIZE * Integer.MAX_VALUE) {
            throw new IllegalArgumentException("初始值超过允许的最大值");
        }
        this.ints = new int[(int)Math.ceil((double)maxVal / BYTE_SIZE) + 1];
    }

    public boolean isUnchecked(long val) {
        // 将待校验值截取 高位 作为位序列索引
        int idx = (int)(val >> 5);

        // 低7位作为待校验值
        int position = 1 << (val & (BYTE_SIZE - 1));
        // 比较byte值对应位上是否为1，即与操作后是否相等
        boolean unset = (this.ints[idx] & position) != position;
        // 将对应位设为1
        this.ints[idx] = this.ints[idx] | position;
        return unset;
    }

    public static void main(String[] args) {
        int c = 0;
        int h = 2160;
        while (h >> 1 > 0) {
            c++;
            h >>= 1;
        }
        System.out.println("c:" + c++);

        IntBitCheck checker = new IntBitCheck(1080 << c | 2160);
        long start = System.nanoTime();
        long startMillis = System.currentTimeMillis();
        for (int i = 0; i < 1080; i++) {
            for (int j = 0; j < 2160; j++) {
                if (!checker.isUnchecked(i << c | j)) {
                    System.err.println(String.format("[%d, %d] 已经校验过", i, j));
                }
            }
        }
        for (int i = 0; i < 1080; i++) {
            for (int j = 0; j < 2160; j++) {
                if (checker.isUnchecked(i << c | j)) {
                    System.err.println(String.format("[%d, %d] 未校验过", i, j));
                }
            }
        }
        long end = System.nanoTime();
        long endMillis = System.currentTimeMillis();
        System.out.println("总耗时：" + (end - start) + "ns");
        System.out.println("总耗时：" + (endMillis - startMillis) + "ms");
        System.out.println("总消耗内存：" + (checker.ints.length * 4) + "字节");
        long val = (((long)(1<<30)) * ((long)(1<<30)) * 2* 2);
        System.out.println(val);
        System.out.println(val *2);
        System.out.println(Long.MAX_VALUE);
        System.out.println((1<<16)/1024);
//        checker.display();
    }

}
