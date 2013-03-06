Solves [JENKINS-16462](https://issues.jenkins-ci.org/browse/JENKINS-16462).

For use with Jenkins core prior to the official fix in 1.501.

Note that this defines a separate parameter type, which you have to explicitly
use, and when you migrate to 1.501+ and turn this off you need to switch back to
the standard parameter type.
