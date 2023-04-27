from typing import Iterable, Iterator, TypeVar, Generator

T = TypeVar("T")


def cons(ary: Iterable[T]) -> Iterator[tuple[T, T]]:
    """Yield pairs of adjacent elements in an array.

    Args:
        ary (Iterable[T]): An iterable.

    Yields:
        Iterator[tuple[T, T]]: A generator of pairs of adjacent elements.
    """

    it = iter(ary)
    prev = next(it)
    for item in it:
        yield prev, item
        prev = item


T1 = TypeVar("T1")
T2 = TypeVar("T2")


def expand_pairs(
    lst: Iterable[tuple[T1, Iterable[T2]]]
) -> Generator[tuple[T1, T2], None, None]:
    """Expands a list of pairs.

    Args:
        lst: List of pairs, in the form [(key, list), ...])]

    Returns:
        List of pairs, in the form [(key, item), ...]
    """

    for key, items in lst:
        for item in items:
            yield (key, item)
