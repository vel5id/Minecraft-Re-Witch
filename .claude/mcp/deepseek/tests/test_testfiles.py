import testfiles


def test_is_test_file_true_cases():
    assert testfiles.is_test_file("hexerei/src/test/java/com/x/FooTest.java")
    assert testfiles.is_test_file("a/b/MathUtilsTest.java")
    assert testfiles.is_test_file("tests/test_bar.py")
    assert testfiles.is_test_file("pkg/bar_test.py")
    assert testfiles.is_test_file("src/foo.test.ts")
    assert testfiles.is_test_file("src/foo.spec.tsx")


def test_is_test_file_false_cases():
    assert not testfiles.is_test_file("hexerei/src/main/java/com/x/Foo.java")
    assert not testfiles.is_test_file("README.md")
    assert not testfiles.is_test_file("src/main/resources/lang/en_us.json")


def test_partition_preserves_split_and_order():
    test, src = testfiles.partition([
        "hexerei/src/test/java/A.java",
        "hexerei/src/main/java/B.java",
        "x_test.py",
        "docs/readme.md",
    ])
    assert test == ["hexerei/src/test/java/A.java", "x_test.py"]
    assert src == ["hexerei/src/main/java/B.java", "docs/readme.md"]
