/*
 *          Twittnuker - Twitter client for Android
 *
 *  Copyright 2013-2017 vanita5 <mail@vanit.as>
 *
 *          This program incorporates a modified version of
 *          Twidere - Twitter client for Android
 *
 *  Copyright 2012-2017 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package de.vanita5.microblog.library.mastodon.api;

import de.vanita5.microblog.library.MicroBlogException;
import de.vanita5.microblog.library.mastodon.model.LinkHeaderList;
import de.vanita5.microblog.library.mastodon.model.Status;
import de.vanita5.microblog.library.twitter.model.Paging;
import org.mariotaku.restfu.annotation.method.GET;
import org.mariotaku.restfu.annotation.param.BooleanEncoding;
import org.mariotaku.restfu.annotation.param.Path;
import org.mariotaku.restfu.annotation.param.Query;


public interface TimelinesResources {
    @GET("/v1/timelines/home")
    LinkHeaderList<Status> getHomeTimeline(@Query Paging paging) throws MicroBlogException;

    @GET("/v1/timelines/public")
    LinkHeaderList<Status> getPublicTimeline(@Query Paging paging, @Query(value = "local",
            booleanEncoding = BooleanEncoding.IGNORE_IF_FALSE) boolean local)
            throws MicroBlogException;

    @GET("/v1/timelines/tag/{tag}")
    LinkHeaderList<Status> getHashtagTimeline(@Path("tag") String hashtag, @Query Paging paging,
            @Query(value = "local", booleanEncoding = BooleanEncoding.IGNORE_IF_FALSE) boolean local)
            throws MicroBlogException;
}