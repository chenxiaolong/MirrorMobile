<!--
    When adding new changelog entries, use [Issue #0] to link to issues and
    [PR #0] to link to pull requests. Then run:

        ./gradlew changelogUpdateLinks

    to update the actual links at the bottom of the file.
-->

### Version 2.0

* Port UI to Jetpack Compose and adopt Material 3 Expressive styling ([PR #13])
* Update AGP to 9.0.0 ([PR #8])
* Reenable default proguard optimizations ([PR #9], [PR #10])
  * For folks who want to decode stack traces from log files, the mapping file is now included with the official releases in `mappings.txt.zst`
* Minor bug fix for long-clickable preferences ([PR #11])
* Fix titles of switch preferences being truncated when they don't fit ([PR #12])

### Version 1.3

* Remove dependency info block from APK ([PR #6])
* Update dependencies ([PR #7])

### Version 1.2

* Properly fix the launching of the screen capture permission prompt ([PR #4])
* Update dependencies and target API 36 ([PR #5])

### Version 1.1

* Update dependencies ([PR #2])
* Work around issue where screen capture permission prompt won't show on newer versions of Android or Android Auto ([PR #3])

### Version 1.0

* Initial release

<!-- Do not manually edit the lines below. Use `./gradlew changelogUpdateLinks` to regenerate. -->
[PR #2]: https://github.com/chenxiaolong/MirrorMobile/pull/2
[PR #3]: https://github.com/chenxiaolong/MirrorMobile/pull/3
[PR #4]: https://github.com/chenxiaolong/MirrorMobile/pull/4
[PR #5]: https://github.com/chenxiaolong/MirrorMobile/pull/5
[PR #6]: https://github.com/chenxiaolong/MirrorMobile/pull/6
[PR #7]: https://github.com/chenxiaolong/MirrorMobile/pull/7
[PR #8]: https://github.com/chenxiaolong/MirrorMobile/pull/8
[PR #9]: https://github.com/chenxiaolong/MirrorMobile/pull/9
[PR #10]: https://github.com/chenxiaolong/MirrorMobile/pull/10
[PR #11]: https://github.com/chenxiaolong/MirrorMobile/pull/11
[PR #12]: https://github.com/chenxiaolong/MirrorMobile/pull/12
[PR #13]: https://github.com/chenxiaolong/MirrorMobile/pull/13
