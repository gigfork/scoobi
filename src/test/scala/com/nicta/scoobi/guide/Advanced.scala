/**
 * Copyright 2011,2012 National ICT Australia Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nicta.scoobi
package guide

class Advanced extends ScoobiPage { def is = "Advanced Notes".title^
                                                                                                                        """
### Configuration Options

Scoobi allows you to configure a few useful runtime settings:

* Use a non-standard working directory by passing `-Dscoobi.workdir` to a `ScoobiApp`
* Set an upper-bound on the amount of reducers scoobi will use with `ScoobiConfiguration.setMaxReducers`
* Set a lower-bound on the amount of reducers scoobi use with `ScoobiConfiguration.setMinReducers`
* Set the amount of reducers scoobi picks, with `ScoobiConfiguration.setBytesPerReducer` (Note: this is based on the input to a MapReduce job, not the input to the reducer. Default is 1GiB)

Look in `ScoobiConfiguration` for other useful runtime configuration options

### Static References

Values or objects that are behind a final static variable or reference won't get serialised properly. They get serialised in their initial state, not their current state. Very often this initial state might be null (for references) and 0 for Ints etc. So our recommendation is to not use them at all. And to avoid hitting the problem, don't use DelayedInit (it internally works with them) and always prefer a `val` to a `var` (especially considering variables are not shared between map-reduce jobs)


### DList Covariance

At the moment DList is not covariant (that is, a `DList[Apple]` is not a `DList[Fruit]`). This is something we're slowly working on, but is quite a large issue because of its flow-on effects"""
}
