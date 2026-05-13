/// 简单字符串拼接
fn concat_str(a: &str, b: &str) -> String {
    format!("{}_{}", a, b)
}

/// 判断数字是否为正数
fn is_positive(n: i32) -> bool {
    n > 0
}

// 测试调用
fn main() {
    let res = concat_str("credit", "score");
    println!("拼接结果：{}", res);

    println!("10 是否正数：{}", is_positive(10));
    println!("-5 是否正数：{}", is_positive(-5));
}